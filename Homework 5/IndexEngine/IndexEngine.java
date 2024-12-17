import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class IndexEngine {
    private static BufferedWriter docNoWriter = null;
    static int internalId = 1;
    private static Map<Integer, String> inMemoryLaTimes;
    private static Map<Integer, List<String>> docToTokens;
    private static Map<String, Integer> lexicon;
    private static Map<Integer, List<DocIdCountPair>> invertedIndex;
    private static List<Integer> docLengths;
    private static String outputPathToFiles;
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Error: Two arguments required (inputPath outputPath). Application now exiting.");
            System.exit(1);
        }
    
        String inputPath = args[0];
        String outputPath = args[1];

        Path filePath = Paths.get(inputPath);

        // Check if the path exists
        if (!Files.exists(filePath)) {
            System.out.println("Path doesn't exist in arguement passed.");
            System.exit(1);
        } 
    
        try {
            invertedIndex = new HashMap<Integer, List<DocIdCountPair>>();
            lexicon = new HashMap<String, Integer>();
            docLengths = new ArrayList<>();

            processGZippedfiles(inputPath, outputPath);
            //write invertedIndex to file
            writeLexiconToFile(lexicon, outputPathToFiles);
            writeInvertedIndexToFile(invertedIndex, outputPathToFiles);
            writeDocLengthsToFile(docLengths, outputPathToFiles);
            System.out.println("Finished Indexing");
        } catch (FileNotFoundException e) {
            System.out.println("Error: The specified input file was not found: " + inputPath);
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Error: An I/O error occurred while processing the files.");
            System.exit(1);
        }
    }

    public static void processGZippedfiles(String inputPath, String outputPath) throws FileNotFoundException, IOException{
        File outputFile = new File(outputPath);
        if (outputFile.exists()) {
            System.out.println("Output path already exists");
            System.exit(1);
        }
        try{
            InputStream fileStream = new FileInputStream(inputPath);
            InputStream gzipStream = new GZIPInputStream(fileStream);
            Reader decoder = new InputStreamReader(gzipStream);
            BufferedReader buffered = new BufferedReader(decoder);
            processFiles(buffered, outputPath);
        }catch(FileNotFoundException e){
            System.out.println(e);
        }catch(IOException e){
            System.out.println(e);
        }
    }

    private static void processFiles(BufferedReader buffered, String outputPath) throws IOException {
        try {
            String docNo = "", dirFilePath = "", headline = "", date = "", line;
            File textFilePath = null;
            StringBuffer sb = new StringBuffer();
            boolean inHeadline = false, inDate = false, isDateNext = false;

            while ((line = buffered.readLine()) != null) {

                sb.append(line + "\n");

                if (!line.contains("<DOCNO>") && !line.contains("</DOCNO>") && !line.contains("<DATE>") 
                    && !inDate && !isDateNext &&  !line.contains("</DATE>") && !inHeadline && !line.contains("<HEADLINE>") && !line.contains("</DOC>")) {
                    continue;
                }
                if (line.contains("<DOCNO>")) {
                    docNo = getDocNo(line);
                    dirFilePath = getFilePath(line, outputPath);
                    textFilePath = new File(dirFilePath);
                    textFilePath.mkdirs();
                }

                // Extract the date
                if (inDate && isDateNext) {
                    date += getUpToSecondComma(line.trim());
                    isDateNext = false;
                    inDate = false;
                }
                
                if (line.contains("<DATE>") || inDate) {
                    inDate = true;
                    if (line.contains("<P>")) {
                        isDateNext = true;
                    }
                } else {
                    inDate = false;
                }

                //Extract the headline
                if(line.contains("<HEADLINE>") || inHeadline){
                    inHeadline = true;
                }

                if(inHeadline){
                    headline += line;
                }

                if(line.contains("</HEADLINE>")){
                    inHeadline = false;
                }

                // reached the end of the file, write it
                if(line.contains("</DOC>")){
                    generateMapping(docNo, outputPath, textFilePath);
                    if(textFilePath != null){
                        String hl = extractHeadline(headline);
                        writeMetaDataFile(docNo, internalId, date, hl, outputPath);
                        writeFile(docNo, internalId++, date, hl, sb, textFilePath);
                        outputPathToFiles = outputPath;
                    }
                    sb.setLength(0);
                    date = headline = docNo = "";
                    textFilePath = null;
                }
            }
        } catch (FileNotFoundException e) {
            System.out.println(e);
        } catch (IOException e) {
            System.out.println(e);
        } finally {
            if (buffered != null) {
                buffered.close();
            }
        }
    }

    private static String extractHeadline(String input) {
        StringBuilder result = new StringBuilder();
        
        Pattern pattern = Pattern.compile("<P>(.*?)</P>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            result.append(matcher.group(1).trim()).append(" ");
        }
        
        return result.toString().trim();
    }

    private static String getUpToSecondComma(String input) {
        int secondCommaIndex = input.indexOf(",", input.indexOf(",") + 1);
        return secondCommaIndex == -1 ? input : input.substring(0, secondCommaIndex);
    }

    private static void writeFile(String docNo, int internalId, String date, String headline, StringBuffer sb, File textFilePath) throws IOException {
        File file = new File(textFilePath.toString() + "/" + docNo + ".txt");
        inMemoryLaTimes = new HashMap<Integer, String>();

        List<String> tokens = new ArrayList<>();
        // mapping for docId to Tokens
        docToTokens = new HashMap<Integer, List<String>>();
        // remove all tags
        String documentContents = removeAllTags(sb);
        // tokenize
        tokenize(documentContents, tokens);
        docLengths.add(tokens.size());
        // store in-memory version of the LA-times collection and the tokens
        inMemoryLaTimes.put(internalId, documentContents);
        docToTokens.put(internalId, tokens);
        // store the lexicon
        populateLexicon(tokens);
        //populate postings list
        populateInvertedIndex(tokens, internalId);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(sb.toString());
        }catch(IOException e){
            System.out.println(e);
        }
    }

    private static void writeInvertedIndexToFile(Map<Integer, List<DocIdCountPair>> invertedIndex, String filePath) {
        String invertedIndexPath = filePath + "/invertedIndex/";
        new File(invertedIndexPath).mkdirs();
    
        String fileName = invertedIndexPath + "invertedIndex.txt";
    
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName, true))) {
            for (Map.Entry<Integer, List<DocIdCountPair>> entry : invertedIndex.entrySet()) {
                int termId = entry.getKey();
                List<DocIdCountPair> postingsList = entry.getValue();
    
                // Write the term ID
                writer.write("Term ID: " + termId);
                writer.newLine();
    
                // Write the postings list
                for (DocIdCountPair pair : postingsList) {
                        String line = "    DocID: " + pair.getDocId() + ", Count: " + pair.getCount();
                        writer.write(line);
                        writer.newLine();
                }
    
                writer.write("----------");
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void writeDocLengthsToFile(List<Integer> docLengths, String filePath){
        String docLengthsPath = filePath + "/doc-lengths/";

        new File(docLengthsPath).mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(docLengthsPath + "doc-lengths.txt"))) {
            for (int length : docLengths) {                
                writer.write(Integer.toString(length));
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void writeLexiconToFile(Map<String, Integer> lexicon, String filePath) {

        String lexiconPath = filePath + "/lexicon/";

        // Create directories if they do not exist
        new File(lexiconPath).mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(lexiconPath + "lexicon.txt"))) {
            for (Map.Entry<String, Integer> entry : lexicon.entrySet()) {
                String term = entry.getKey();
                int termId = entry.getValue();
                
                writer.write(term + "\t" + termId);
                writer.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void populateLexicon(List<String> tokens){
        int lexiconId = lexicon.size() + 1;
        for(String word : tokens){
            if(!lexicon.containsKey(word)){
                lexicon.put(word, lexiconId++);
            }
        }
    }

    private static void populateInvertedIndex(List<String> tokens, int internalId) {
        Map<String, Integer> termCount = new HashMap<>();
    
        for (String word : tokens) {
            termCount.merge(word, 1, Integer::sum);
        }
    
        for (Map.Entry<String, Integer> entry : termCount.entrySet()) {
            String term = entry.getKey();
            int count = entry.getValue();
    
            Integer termId = lexicon.get(term);
    
            if (termId != null) {
                invertedIndex.computeIfAbsent(termId, k -> new ArrayList<>())
                    .add(new DocIdCountPair(internalId, count));
            }
        }
    }

    private static void writeMetaDataFile(String docNo, int internalId, String date, String headline, String outputPath) {
        File metadataDir = new File(outputPath + "/metadata");
        if (!metadataDir.exists()) {
            metadataDir.mkdir();
        }
    
        String filePath = metadataDir + "/" + docNo + "-metadata.txt";
    
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            StringBuilder newContent = new StringBuilder();
            newContent.append("docno: ").append(docNo).append(" ")
                      .append("internal id: ").append(internalId).append(" ")
                      .append("date: ").append(date).append(" ")
                      .append("headline: ").append(headline).append("\n");
            
            writer.write(newContent.toString());
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getFilePath(String line, String outputPath){
        String finalFilePath = "";
        String cleanedDocno = getDocNo(line);
        String[] parts = cleanedDocno.split("-");
        String docNumber = parts[0].trim();

        // Extract the year, month, and day
        String monthStr = docNumber.substring(2, 4); 
        String dayStr = docNumber.substring(4, 6);
        String yearStr = docNumber.substring(6, 8);

        finalFilePath = outputPath + "/" + yearStr + "/" + monthStr + "/" + dayStr;

        return finalFilePath;
    }
    
    private static String getDocNo(String line){
        return line.replace("<DOCNO>", "").replace("</DOCNO>", "").trim();
    }


    private static void generateMapping(String docNo, String outputPath, File textFilePath) throws IOException {
        if (docNoWriter == null) {
            File file = new File(outputPath + "/docnos.txt");
            docNoWriter = new BufferedWriter(new FileWriter(file, true));
        }
    
        docNoWriter.write(docNo + " " + textFilePath.toString() + "/" + docNo + ".txt" + "\n");
        docNoWriter.flush(); 
    }

    private static String removeAllTags(StringBuffer sb){
        String content = sb.toString();
        StringBuilder extractedText = new StringBuilder();
        String[] tags = {"HEADLINE", "TEXT", "GRAPHIC"};
        for (String tag : tags) {
            Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String innerContent = matcher.group(1);
                innerContent = innerContent.replaceAll("<[^>]+>", ""); 
                extractedText.append(innerContent.trim()).append(" ");
            }
        }
        return extractedText.toString();
    }


    static public void tokenize(String text, List<String> tokens) {
        // Convert the text to lowercase
        text = text.toLowerCase();

        int start = 0;
        int i;

        // Iterate through the characters of the text
        for (i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                if (start != i) {
                    // Extract the token and add it to the list
                    String token = text.substring(start, i);
                    tokens.add(token);
                }
                start = i + 1;
            }
        }

        // Handle the last token if necessary
        if (start != i) {
            tokens.add(text.substring(start, i));
        }
    }
}

class DocIdCountPair {
    private int docId;
    private int count;

    public DocIdCountPair() {
        this.docId = 0;  
        this.count = 0;
    }

    public DocIdCountPair(int docId, int count) {
        this.docId = docId;
        this.count = count;
    }

    public int getDocId() {
        return docId;
    }

    public void setDocId(int docId) {
        this.docId = docId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
