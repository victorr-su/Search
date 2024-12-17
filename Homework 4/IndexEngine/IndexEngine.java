import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private static String typeOfRun;
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Error: 3 arguments required. Application now exiting.");
            System.exit(1);
        }
    
        String inputPath = args[0];
        String outputPath = args[1];
        typeOfRun = args[2];

        Path filePath = Paths.get(inputPath);

        // Check if the path exists
        if (!Files.exists(filePath)) {
            System.out.println("Path doesn't exist in arguement passed.");
            System.exit(1);
        } 

        if(!typeOfRun.equals("stem") && !typeOfRun.equals("baseline")){
            System.out.println("Please use stem or baseline");
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

        if(typeOfRun.equals("stem")){
            tokens = tokens.stream()
                        .map(PorterStemmer::stem)
                        .collect(Collectors.toList());
        }

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

        String fileName = "";

        if(typeOfRun.equals("stem")){
            fileName = invertedIndexPath + "stemmedInvertedIndex.txt";
        }else{
            fileName = invertedIndexPath + "invertedIndex.txt";
        }
        
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

        String fileName = "";

        if(typeOfRun.equals("stem")){
            fileName = lexiconPath + "stemmedLexicon.txt";
        }else{
            fileName = lexiconPath + "lexicon.txt";
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            for (Map.Entry<String, Integer> entry : lexicon.entrySet()) {
                String term = entry.getKey();
                int termId = entry.getValue();
                
                writer.write(term + ":" + termId);
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

class PorterStemmer {
    /**
     * Returns the stemed version of str
     *
     *@param str - word to stem
     *
     *@return If the word can not be stemmed or there
     * is some sort of error str is returned.
     */
    public static String stem(String str)
    {
        try
        {
            String results = internalStem( str );
            if (results != null)
                return results;
            else
                return str;
        }
        catch ( Throwable t ) // best way in Java to catch all
        {
            return str;
        }
    }
    private static String internalStem(String str) 
    {
      // check for zero length
      if (str.length() > 0) {
          // all characters must be letters
          char[] c = str.toCharArray();
          for (int i = 0; i < c.length; i++) {
              if (!Character.isLetter(c[i]))
                  return null;
          }
    } else {
        return "No term entered";
    }
    str = step1a(str);
    str = step1b(str);
    str = step1c(str);
    str = step2(str);
    str = step3(str);
    str = step4(str);
    str = step5a(str);
    str = step5b(str);
    return str;
} // end stem
protected static String step1a (String str) {
    // SSES -> SS
    if (str.endsWith("sses")) {
        return str.substring(0, str.length() - 2);
    // IES -> I
    } else if (str.endsWith("ies")) {
        return str.substring(0, str.length() - 2);
    // SS -> S
    } else if (str.endsWith("ss")) {
        return str;
    // S ->
    } else if (str.endsWith("s")) {
        return str.substring(0, str.length() - 1);
    } else {
        return str;
    }
} // end step1a
protected static String step1b (String str) {
    // (m > 0) EED -> EE
    if (str.endsWith("eed")) {
        if (stringMeasure(str.substring(0, str.length() - 3)) > 0)
            return str.substring(0, str.length() - 1);
        else
            return str;
    // (*v*) ED ->
    } else if ((str.endsWith("ed")) &&
               (containsVowel(str.substring(0, str.length() - 2)))) {
        return step1b2(str.substring(0, str.length() - 2));
    // (*v*) ING ->
    } else if ((str.endsWith("ing")) &&
               (containsVowel(str.substring(0, str.length() - 3)))) {
        return step1b2(str.substring(0, str.length() - 3));
    } // end if
    return str;
} // end step1b
protected static String step1b2 (String str) {
    // AT -> ATE
    if (str.endsWith("at") ||
        str.endsWith("bl") ||
        str.endsWith("iz")) {
        return str + "e";
    } else if ((endsWithDoubleConsonent(str)) &&
               (!(str.endsWith("l") || str.endsWith("s") || str.endsWith("z")))) {
        return str.substring(0, str.length() - 1);
    } else if ((stringMeasure(str) == 1) &&
               (endsWithCVC(str))) {
        return str + "e";
    } else {
        return str;
    }
} // end step1b2
protected static String step1c(String str) {
    // (*v*) Y -> I
    if (str.endsWith("y")) {
        if (containsVowel(str.substring(0, str.length() - 1)))
            return str.substring(0, str.length() - 1) + "i";
    } // end if
    return str;
} // end step1c
protected static String step2 (String str) {
    // (m > 0) ATIONAL -> ATE
    if ((str.endsWith("ational")) &&
        (stringMeasure(str.substring(0, str.length() - 5)) > 0)) {
        return str.substring(0, str.length() - 5) + "e";
    // (m > 0) TIONAL -> TION
    } else if ((str.endsWith("tional")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
        return str.substring(0, str.length() - 2);
    // (m > 0) ENCI -> ENCE
    } else if ((str.endsWith("enci")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
        return str.substring(0, str.length() - 2);
    // (m > 0) ANCI -> ANCE
    } else if ((str.endsWith("anci")) &&
        (stringMeasure(str.substring(0, str.length() - 1)) > 0)) {
        return str.substring(0, str.length() - 1) + "e";
    // (m > 0) IZER -> IZE
    } else if ((str.endsWith("izer")) &&
        (stringMeasure(str.substring(0, str.length() - 1)) > 0)) {
        return str.substring(0, str.length() - 1);
    // (m > 0) ABLI -> ABLE
    } else if ((str.endsWith("abli")) &&
        (stringMeasure(str.substring(0, str.length() - 1)) > 0)) {
        return str.substring(0, str.length() - 1) + "e";
    // (m > 0) ENTLI -> ENT
    } else if ((str.endsWith("alli")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
        return str.substring(0, str.length() - 2);
    // (m > 0) ELI -> E
    } else if ((str.endsWith("entli")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
        return str.substring(0, str.length() - 2);
    // (m > 0) OUSLI -> OUS
    } else if ((str.endsWith("eli")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
        return str.substring(0, str.length() - 2);
    // (m > 0) IZATION -> IZE
    } else if ((str.endsWith("ousli")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
        return str.substring(0, str.length() - 2);
    // (m > 0) IZATION -> IZE
    } else if ((str.endsWith("ization")) &&
        (stringMeasure(str.substring(0, str.length() - 5)) > 0)) {
        return str.substring(0, str.length() - 5) + "e";
    // (m > 0) ATION -> ATE
    } else if ((str.endsWith("ation")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
        return str.substring(0, str.length() - 3) + "e";
    // (m > 0) ATOR -> ATE
    } else if ((str.endsWith("ator")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
        return str.substring(0, str.length() - 2) + "e";
    // (m > 0) ALISM -> AL
    } else if ((str.endsWith("alism")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
       return str.substring(0, str.length() - 3);
    // (m > 0) IVENESS -> IVE
    } else if ((str.endsWith("iveness")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 0)) {
        return str.substring(0, str.length() - 4);
    // (m > 0) FULNESS -> FUL
    } else if ((str.endsWith("fulness")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 0)) {
        return str.substring(0, str.length() - 4);
    // (m > 0) OUSNESS -> OUS
    } else if ((str.endsWith("ousness")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 0)) {
        return str.substring(0, str.length() - 4);
    // (m > 0) ALITII -> AL
    } else if ((str.endsWith("aliti")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
        return str.substring(0, str.length() - 3);
    // (m > 0) IVITI -> IVE
    } else if ((str.endsWith("iviti")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
        return str.substring(0, str.length() - 3) + "e";
    // (m > 0) BILITI -> BLE
    } else if ((str.endsWith("biliti")) &&
        (stringMeasure(str.substring(0, str.length() - 5)) > 0)) {
        return str.substring(0, str.length() - 5) + "le";
    } // end if
    return str;
} // end step2
protected static String step3 (String str) {
    // (m > 0) ICATE -> IC
    if ((str.endsWith("icate")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
        return str.substring(0, str.length() - 3);
    // (m > 0) ATIVE ->
    } else if ((str.endsWith("ative")) &&
        (stringMeasure(str.substring(0, str.length() - 5)) > 0)) {
        return str.substring(0, str.length() - 5);
    // (m > 0) ALIZE -> AL
    } else if ((str.endsWith("alize")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
        return str.substring(0, str.length() - 3);
    // (m > 0) ICITI -> IC
    } else if ((str.endsWith("iciti")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
        return str.substring(0, str.length() - 3);
    // (m > 0) ICAL -> IC
    } else if ((str.endsWith("ical")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 0)) {
        return str.substring(0, str.length() - 2);
    // (m > 0) FUL ->
    } else if ((str.endsWith("ful")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 0)) {
        return str.substring(0, str.length() - 3);
    // (m > 0) NESS ->
    } else if ((str.endsWith("ness")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 0)) {
        return str.substring(0, str.length() - 4);
    } // end if
    return str;
} // end step3
protected static String step4 (String str) {
    if ((str.endsWith("al")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 1)) {
        return str.substring(0, str.length() - 2);
        // (m > 1) ANCE ->
    } else if ((str.endsWith("ance")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
        return str.substring(0, str.length() - 4);
    // (m > 1) ENCE ->
    } else if ((str.endsWith("ence")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
        return str.substring(0, str.length() - 4);
    // (m > 1) ER ->
    } else if ((str.endsWith("er")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 1)) {
        return str.substring(0, str.length() - 2);
    // (m > 1) IC ->
    } else if ((str.endsWith("ic")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 1)) {
        return str.substring(0, str.length() - 2);
    // (m > 1) ABLE ->
    } else if ((str.endsWith("able")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
        return str.substring(0, str.length() - 4);
    // (m > 1) IBLE ->
    } else if ((str.endsWith("ible")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
        return str.substring(0, str.length() - 4);
    // (m > 1) ANT ->
    } else if ((str.endsWith("ant")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    // (m > 1) EMENT ->
    } else if ((str.endsWith("ement")) &&
        (stringMeasure(str.substring(0, str.length() - 5)) > 1)) {
        return str.substring(0, str.length() - 5);
    // (m > 1) MENT ->
    } else if ((str.endsWith("ment")) &&
        (stringMeasure(str.substring(0, str.length() - 4)) > 1)) {
        return str.substring(0, str.length() - 4);
    // (m > 1) ENT ->
    } else if ((str.endsWith("ent")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    // (m > 1) and (*S or *T) ION ->
    } else if ((str.endsWith("sion") || str.endsWith("tion")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    // (m > 1) OU ->
    } else if ((str.endsWith("ou")) &&
        (stringMeasure(str.substring(0, str.length() - 2)) > 1)) {
        return str.substring(0, str.length() - 2);
    // (m > 1) ISM ->
    } else if ((str.endsWith("ism")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    // (m > 1) ATE ->
    } else if ((str.endsWith("ate")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    // (m > 1) ITI ->
    } else if ((str.endsWith("iti")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    // (m > 1) OUS ->
    } else if ((str.endsWith("ous")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    // (m > 1) IVE ->
    } else if ((str.endsWith("ive")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    // (m > 1) IZE ->
    } else if ((str.endsWith("ize")) &&
        (stringMeasure(str.substring(0, str.length() - 3)) > 1)) {
        return str.substring(0, str.length() - 3);
    } // end if
    return str;
} // end step4
protected static String step5a (String str) {
    if (str.length() == 0) return str;  //gets rid of an exception...
    // (m > 1) E ->
    if ((stringMeasure(str.substring(0, str.length() - 1)) > 1) &&
        str.endsWith("e"))
        return str.substring(0, str.length() -1);
    // (m = 1 and not *0) E ->
    else if ((stringMeasure(str.substring(0, str.length() - 1)) == 1) &&
             (!endsWithCVC(str.substring(0, str.length() - 1))) &&
             (str.endsWith("e")))
        return str.substring(0, str.length() - 1);
    else
        return str;
} // end step5a
protected static String step5b (String str) {
  if (str.length() == 0) return str;
    // (m > 1 and *d and *L) ->
    if (str.endsWith("l") &&
        endsWithDoubleConsonent(str) &&
        (stringMeasure(str.substring(0, str.length() - 1)) > 1)) {
        return str.substring(0, str.length() - 1);
    } else {
        return str;
    }
} // end step5b
/*
   -------------------------------------------------------
   The following are functions to help compute steps 1 - 5
   -------------------------------------------------------
*/
// does string end with 's'?
protected static boolean endsWithS(String str) {
    return str.endsWith("s");
} // end function
// does string contain a vowel?
protected static boolean containsVowel(String str) {
    char[] strchars = str.toCharArray();
    for (int i = 0; i < strchars.length; i++) {
        if (isVowel(strchars[i]))
            return true;
    }
    // no aeiou but there is y
    if (str.indexOf('y') > -1)
        return true;
    else
        return false;
} // end function
// is char a vowel?
public static boolean isVowel(char c) {
    if ((c == 'a') ||
        (c == 'e') ||
        (c == 'i') ||
        (c == 'o') ||
        (c == 'u'))
        return true;
    else
        return false;
} // end function
// does string end with a double consonent?
protected static boolean endsWithDoubleConsonent(String str) {
if (str.length() < 2) return false;
    char c = str.charAt(str.length() - 1);
    if (c == str.charAt(str.length() - 2))
        if (!containsVowel(str.substring(str.length() - 2))) {
            return true;
    }
    return false;
} // end function
// returns a CVC measure for the string
protected static int stringMeasure(String str) {
    int count = 0;
    boolean vowelSeen = false;
    char[] strchars = str.toCharArray();
    for (int i = 0; i < strchars.length; i++) {
        if (isVowel(strchars[i])) {
            vowelSeen = true;
        } else if (vowelSeen) {
            count++;
            vowelSeen = false;
        }
    } // end for
    return count;
} // end function
// does stem end with CVC?
protected static boolean endsWithCVC (String str) {
    char c, v, c2 = ' ';
    if (str.length() >= 3) {
        c = str.charAt(str.length() - 1);
        v = str.charAt(str.length() - 2);
        c2 = str.charAt(str.length() - 3);
    } else {
        return false;
    }
    if ((c == 'w') || (c == 'x') || (c == 'y')) {
        return false;
    } else if (isVowel(c)) {
        return false;
    } else if (!isVowel(v)) {
        return false;
    } else if (isVowel(c2)) {
        return false;
    } else {
        return true;
    }
} // end function
} // end class