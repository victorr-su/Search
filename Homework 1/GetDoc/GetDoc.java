
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GetDoc {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Error: Two arguments required (inputPath outputPath). Application now exiting.");
            System.exit(1);
        }

        String path = args[0];
        String type = args[1];
        String identifier = args[2];

        // check if path exists
        Path verPath = Paths.get(path);

        // Check if the path exists
        if (!Files.exists(verPath)) {
            System.out.println("Path doesn't exist in arguement passed.");
            System.exit(1);
        } 
        
        try {
            if (type.equals("docno") || type.equals("id")) {
                HashMap <String, String> DocNoToPath = new HashMap<String, String>();
                List<String> idToPath = new ArrayList<String>();

                //for getting the metadata from internalId
                HashMap<Integer, String> idToDocNo = new HashMap<Integer, String>();
                
                loadMapping(DocNoToPath, idToPath, path, idToDocNo, identifier, type);
                if(type.equals("docno")){
                    getDocumentByDocNo(identifier, DocNoToPath, path);
                }else if (type.equals("id")){
                    getDocumentById(identifier, idToPath, path, idToDocNo);
                }
            } else{
                System.out.println("Please enter a valid identifier (docno or id)");
                System.exit(1);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Error: The specified input file was not found: " + path);
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Error: An I/O error occurred while processing the files.");
            System.exit(1);
        }
    }

    private static void loadMapping(HashMap <String, String> DocNoToPath, List<String> idToPath, String filePath, HashMap <Integer, String> idToDocNo, String identifier, String type){
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath + "docnos.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Split the line by space
                String[] parts = line.split(" ", 2);

                if (parts.length == 2) {
                    DocNoToPath.put(parts[0], parts[1]);
                    idToPath.add(parts[1]);
                }

                //map the id to the docno
                if(type.equals("id") && idToPath.size() >= Integer.parseInt(identifier)){
                    idToDocNo.put(Integer.parseInt(identifier), extractLastLAWord(idToPath.get(Integer.parseInt(identifier)-1)));
                }

            }
        } catch (IOException e) {
            System.out.println("Error reading the file: " + e.getMessage());
        }
    }

    private static String extractLastLAWord(String path) {
        String[] words = path.split("[/ ]");
        String lastLAWord = null;

        for (String word : words) {
            if (word.startsWith("LA")) {
                lastLAWord = word; 
                lastLAWord = lastLAWord.substring(0, lastLAWord.length() - 4);
            }
        }
        return lastLAWord;
    }

    private static void getDocumentByDocNo(String identifier, HashMap <String, String> DocNoToPath, String path) throws FileNotFoundException, IOException {
        String filePath = DocNoToPath.get(identifier);
        String metadataPath = path + "metadata/" + identifier + "-metadata.txt";

        if(!DocNoToPath.containsKey(identifier)){
            System.out.println("No document with that docno");
            System.exit(1);
        }

        File documentFile = new File(filePath);

        File metadataFile = new File(metadataPath);
        
        read(documentFile, identifier, metadataFile);
    }

    private static void getDocumentById(String identifier, List<String> idToPath, String path, HashMap<Integer, String> idToDocNo){
        // get the docno from the internalId

        int internalId = Integer.parseInt(identifier);
        if(internalId > idToPath.size()){
            System.out.println("No document with that ID");
            System.exit(1);
        }

        String docNo = idToDocNo.get(Integer.parseInt(identifier));
        String metadataPath = path + "metadata/" + docNo + "-metadata.txt";

        File documentFile = new File(idToPath.get(internalId - 1));
        File metadataFile = new File(metadataPath);


        read(documentFile, identifier, metadataFile);
    }

    private static void read(File documentFile, String identifier, File metadataFile){
        if (!documentFile.exists()) {
            System.out.println("Error: Document with DOCNO/id " + identifier + " not found.");
            return;
        }
        // reaading the metadata
        try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" (?=docno:|internal id:|date:|headline:)"); 

                for (String part : parts) {
                    System.out.println(part);
                }
            }
            System.out.println("raw document: ");
        } catch (IOException e) {
            System.out.println("Error reading document: " + e.getMessage());
        }

        //reading the file contents
        try (BufferedReader reader = new BufferedReader(new FileReader(documentFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println("Error reading document: " + e.getMessage());
        }
    }
    
}
