package IndexEngine;
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
            processGZippedfiles(inputPath, outputPath);
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
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(sb.toString());
        }catch(IOException e){
            System.out.println(e);
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
}