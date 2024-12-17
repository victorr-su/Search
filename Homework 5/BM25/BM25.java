import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BM25{
    private static Map<Integer, List<DocIdCountPair>> invertedIndex;
    private static Map<Integer, DocumentMetadata> documentMetadata;
    private static Map<String, Integer> lexicon; 
    private static Map<Integer, String> queries;
    private static Map<Integer, Integer> docLengths;
    private static String typeOfRun;

    private static double avgDocLength;
    private static int totalDocs;

    public static void main(String[] args) throws IOException {

        if (args.length != 5) {
            System.out.println("Please use all 5 arguements when running the program");
            System.exit(1);
        }
        
        String indexDirectory = args[0];
        String queriesFile = args[1];
        String docLengthsFile = args[2];
        String outputFile = args[3];
        typeOfRun = args[4];

        File indexPath = new File(indexDirectory);
        File queriesPath = new File(queriesFile);
        File docLengthsPath = new File(docLengthsFile);

        if(!indexPath.exists()){
            System.out.println("Index Path Doesn't Exist");
            System.exit(1);
        }

        if(!queriesPath.exists()){
            System.out.println("Queries Path Doesn't Exist");
            System.exit(1);
        }

        if(!docLengthsPath.exists()){
            System.out.println("DocLengths Path Doesn't Exist");
            System.exit(1);
        }

        if (!typeOfRun.equals("baseline") && !typeOfRun.equals("stem")) {
            System.err.println("Invalid run type specified. Use 'baseline' or 'stem'.");
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
        // doc lengths
        docLengths = loadDocLengths(docLengthsFile);
        // total docs
        totalDocs = documentMetadata.size();
        // avg doc lengths
        avgDocLength = loadAverageDocLengths();

        // perform BM25, and print results file
        generateBM25Results(outputFile);
    }

    private static void generateBM25Results(String outputFile){
        String runTag = typeOfRun.equals("stem") ? "v3su_bm25_stem" : "v3su_bm25_baseline";
        List<ResultEntry> results = new ArrayList<>();

        for (Map.Entry<Integer, String> queryEntry : queries.entrySet()){
            int topicID = queryEntry.getKey();
            List<String> queryTokens = splitQueryIntoTokens(queryEntry.getValue());
        
            if(typeOfRun.equals("stem")){
                queryTokens = queryTokens.stream()
                .map(PorterStemmer::stem)
                .collect(Collectors.toList());
            }
            Map<Integer, Double> scores = calculateBM25(queryTokens, topicID);

            // after calculating BM25, sort scores in descending order
            List<Map.Entry<Integer, Double>> rankedDocs = scores.entrySet().stream()
            .sorted((a, b) -> {
                int cmp = Double.compare(b.getValue(), a.getValue());
                if (cmp != 0) return cmp;
                return documentMetadata.get(a.getKey()).getDocNo()
                        .compareTo(documentMetadata.get(b.getKey()).getDocNo());
            })
            .limit(1000)
            .collect(Collectors.toList());

            int rank = 1;
            // Populate results with ranked documents for this query
            for (Map.Entry<Integer, Double> entry : rankedDocs) {
                ResultEntry result = new ResultEntry(topicID, "Q0", documentMetadata.get(entry.getKey()).getDocNo() , rank++, entry.getValue(), runTag);
                results.add(result);
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writeToFile(results, outputFile);
            System.out.println("Finished Retrieval");
        } catch (IOException e) {
            System.err.println("Error writing to output file: " + e.getMessage());
        }
    }

    private static Map<Integer, Double> calculateBM25(List<String> queryTokens, int topicID){
        Map<Integer, Double> scores = new HashMap<>();
        double k1 = 1.2;
        double b = 0.75;

            for (String term : queryTokens) {
                if (!lexicon.containsKey(term)) continue; // Skip terms not in the lexicon
                int termID = lexicon.get(term);
    
                List<DocIdCountPair> postingsList = invertedIndex.getOrDefault(termID, new ArrayList<>());
                int n_t = postingsList.size();
                double idf = Math.log((totalDocs - n_t + 0.5) / (n_t + 0.5));
    
                for (DocIdCountPair pair : postingsList) {
                    int docID = pair.getDocId();
                    int f_td = pair.getCount();
                    int docLength = docLengths.get(docID);
    
                    double bm25Score = idf * ((f_td) / (f_td + (k1 * (1 - b + b * (docLength / avgDocLength)))));
                    scores.put(docID, scores.getOrDefault(docID, 0.0) + bm25Score);
                }
    
            }

        return scores;
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

    private static double loadAverageDocLengths(){
        int totalLength = 0;
        for(int docId : docLengths.keySet()){
            totalLength += docLengths.get(docId);
        }
        return totalLength/(double) docLengths.size();
    }

    private static Map<Integer, Integer> loadDocLengths(String filePath) throws IOException {
        Map<Integer, Integer> docLengths = new HashMap<>();
        int internalId = 1;

        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        String line;

        while ((line = reader.readLine()) != null) {
            try {
                int length = Integer.parseInt(line.trim());
                docLengths.put(internalId, length);
                internalId++;
            } catch (NumberFormatException e) {
                System.err.println("Invalid number format in document lengths file: " + line);
            }
        }
        reader.close();

        return docLengths;
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
        
        String lexiconPath = "";

        if(typeOfRun.equals("baseline")){
            lexiconPath = indexPath + "/lexicon/lexicon.txt";
        }else{
            lexiconPath = indexPath + "/lexicon/stemmedLexicon.txt";
        }
    
        try (BufferedReader reader = new BufferedReader(new FileReader(lexiconPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split(":");
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
        String invertedIndexPath = "";

        if(typeOfRun.equals("baseline")){
            invertedIndexPath = indexPath + "/invertedIndex/invertedIndex.txt";
        }else{
            invertedIndexPath = indexPath + "/invertedIndex/stemmedInvertedIndex.txt";
        }

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