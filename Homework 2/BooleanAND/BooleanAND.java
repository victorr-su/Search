package BooleanAND;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BooleanAND {

    private static Map<Integer, List<DocIdCountPair>> invertedIndex;
    private static Map<Integer, DocumentMetadata> documentMetadata;
    private static Map<String, Integer> lexicon; 
    private static Map<Integer, String> queries;
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Please use all 3 arguements when running the program");
            System.exit(1);
        }
        
        String indexDirectory = args[0];
        String queriesFile = args[1];
        String outputFile = args[2];

        File indexPath = new File(indexDirectory);
        File queriesPath = new File(queriesFile);
        
        if(!indexPath.exists()){
            System.out.println("Index Path Doesn't Exist");
            System.exit(1);
        }

        if(!queriesPath.exists()){
            System.out.println("Queries Path Doesn't Exist");
            System.exit(1);
        }

        //read the index
        invertedIndex = loadInvertedIndex(indexDirectory);
        //read the lexicon
        lexicon = loadLexicon(indexDirectory);
        // read all metadata
        documentMetadata = loadDocumentMetadata(indexDirectory);
        // read all the queries
        queries = loadQueries(queriesFile);
        
        // perform BM25, and print results file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            List<ResultEntry> results = generateTrecResults("v3suAND");
            writeToFile(results, outputFile);
            System.out.println("Finished Retrieval");
        } catch (IOException e) {
            System.err.println("Error writing to output file: " + e.getMessage());
        }
    }

    private static List<ResultEntry> generateTrecResults(String runTag) {
        List<ResultEntry> results = new ArrayList<>();

        for (Map.Entry<Integer, String> entry : queries.entrySet()) {
            int topicID = entry.getKey();
            String query = entry.getValue();
            Set<Integer> retrievedDocs = performBooleanANDSearch(query);

            int numRetrieved = retrievedDocs.size();

            // order the rankings
            int rank = 1;
            for (int docId : retrievedDocs) {
                DocumentMetadata metadata = documentMetadata.get(docId);
                if (metadata != null) {
                    String docNo = metadata.getDocNo();
                    double score = numRetrieved - rank;

                    results.add(new ResultEntry(topicID, "Q0", docNo, rank++, score, runTag));
                }
            }
        }

        results.sort(Comparator.comparing(ResultEntry::getTopicID)
                .thenComparing(ResultEntry::getScore, Comparator.reverseOrder()));

        return results;
    }

    private static Set<Integer> performBooleanANDSearch(String query) {
        // Extract the words in the query term
        List<String> words = splitQueryIntoTokens(query);
    
        Set<Integer> resultSet = null;
    
        // Logic for Boolean AND
        for (String word : words) {
            // Handle the null cases
            Integer termId = lexicon.get(word);
            if (termId == null) {
                return Collections.emptySet();
            }
    
            List<DocIdCountPair> postingsList = invertedIndex.get(termId);
            if (postingsList == null) {
                return Collections.emptySet();
            }
    
            Set<Integer> currentDocIds = new HashSet<>();
            for (DocIdCountPair pair : postingsList) {
                currentDocIds.add(pair.getDocId());
            }
    
            // If it's the first term, initialize resultSet with currentDocIds
            if (resultSet == null) {
                resultSet = new HashSet<>(currentDocIds);
            } else {
                resultSet.retainAll(currentDocIds);
            }
    
            if (resultSet.isEmpty()) {
                return Collections.emptySet();
            }
        }
    
        return resultSet != null ? resultSet : Collections.emptySet();
    }
    

    private static void writeToFile(List<ResultEntry> results, String outputFile) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (ResultEntry result : results) {
                writer.write(result.toString());
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to the results file: " + e.getMessage());
        }
    }

    private static Map<Integer, String> loadQueries(String queriesFile) {
        Map<Integer, String> queries = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(queriesFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String topicNumber = line.trim();
                if ((line = reader.readLine()) != null) {
                    String query = line.trim();
                    queries.put(Integer.parseInt(topicNumber), query);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading queries file: " + e.getMessage());
        }
        return queries;
    }

    private static Map<String, Integer> loadLexicon(String indexPath) {
        Map<String, Integer> lexicon = new HashMap<>();
        String lexiconPath = indexPath + "/lexicon/lexicon.txt";
    
        try (BufferedReader reader = new BufferedReader(new FileReader(lexiconPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2) {
                    lexicon.put(parts[0], Integer.parseInt(parts[1]));
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading lexicon file: " + e.getMessage());
        } catch (NumberFormatException e) {
            System.err.println("Error parsing term ID: " + e.getMessage());
        }
    
        return lexicon;
    }


    private static Map<Integer, List<DocIdCountPair>> loadInvertedIndex(String indexPath) {
        Map<Integer, List<DocIdCountPair>> index = new HashMap<>();
        String invertedIndexPath = indexPath + "/invertedIndex/invertedIndex.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(invertedIndexPath))) {
            String line;
            Integer currentTermId = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim(); 

                if (line.startsWith("Term ID:")) {
                    currentTermId = Integer.parseInt(line.split(":")[1].trim());
                    index.put(currentTermId, new ArrayList<>());
                } else if (line.startsWith("DocID:") && currentTermId != null) {
                    String[] parts = line.split(",");
                    Integer docId = Integer.parseInt(parts[0].split(":")[1].trim());
                    Integer count = Integer.parseInt(parts[1].split(":")[1].trim());

                    index.get(currentTermId).add(new DocIdCountPair(docId, count));
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading inverted index file: " + e.getMessage());
        }

        return index;
    }

    private static Map<Integer, DocumentMetadata> loadDocumentMetadata(String indexPath) {
        Map<Integer, DocumentMetadata> metadataMap = new HashMap<>();
        
        File dir = new File(indexPath + "/metadata");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));

        if (files != null) {
            int internalId = -1;
            for (File file : files) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    String docNo = "";
                    String date = "";
                    String headline = "";

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        String[] parts = line.split(" ");
                        for (int i = 0; i < parts.length; i++) {
                            switch (parts[i]) {
                                case "docno:":
                                    docNo = parts[++i];
                                    break;
                                case "id:":
                                    internalId = Integer.parseInt(parts[++i]);
                                    break;
                            }
                        }
                    }
                    metadataMap.put(internalId, new DocumentMetadata(docNo, internalId, date, headline));
                } catch (IOException e) {
                    System.err.println("Error reading metadata file: " + e.getMessage());
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing internal ID: " + e.getMessage());
                }
            }
        } else {
            System.err.println("No metadata files found in the specified directory.");
        }

        return metadataMap;
    }

    private static List<String> splitQueryIntoTokens(String query) {
        List<String> tokens = new ArrayList<>();
        query = query.toLowerCase();

        int start = 0;
        int i;

        for (i = 0; i < query.length(); ++i) {
            char c = query.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                if (start != i) {
                    String token = query.substring(start, i);
                    tokens.add(token);
                }
                start = i + 1;
            }
        }

        if (start != i) {
            tokens.add(query.substring(start, i));
        }

        return tokens; 
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

class DocumentMetadata {
    private String docNo;
    private int internalId;
    private String date;
    private String headline;

    public DocumentMetadata(String docNo, int internalId, String date, String headline) {
        this.docNo = docNo;
        this.internalId = internalId;
        this.date = date;
        this.headline = headline;
    }

    public String getDocNo() {
        return docNo;
    }

    public int getInternalId() {
        return internalId;
    }

    public String getDate() {
        return date;
    }

    public String getHeadline() {
        return headline;
    }
}

class ResultEntry {
    private int topicID;
    private String q0;
    private String docNo;
    private int rank;
    private double score;
    private String runTag;

    public ResultEntry(int topicID, String q0, String docNo, int rank, double score, String runTag) {
        this.topicID = topicID;
        this.q0 = q0;
        this.docNo = docNo;
        this.rank = rank;
        this.score = score;
        this.runTag = runTag;
    }

    public int getTopicID() {
        return topicID;
    }

    public double getScore() {
        return score;
    }

    public String toString() {
        return topicID + " " + q0 + " " + docNo + " " + rank + " " + score + " " + runTag;
    }

}
