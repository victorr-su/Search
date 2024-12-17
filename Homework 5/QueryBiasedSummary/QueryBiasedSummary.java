import java.util.zip.GZIPInputStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.io.*;

public class QueryBiasedSummary {
    static int internalId = 1;
    private static Map<Integer, List<DocIdCountPair>> invertedIndex;
    private static Map<Integer, DocumentMetadata> documentMetadata;
    private static Map<String, Integer> lexicon; 
    private static Map<String, Document> documents;
    private static Map<Integer, Integer> docLengths;
    private static int totalDocs;
    private static double avgDocLength;
    private static String docLengthsFile = "/Users/victorsu/Desktop/MSE-541/latimes-index/doc-lengths/doc-lengths.txt";

    private static final String indexDirectory = "/Users/victorsu/Desktop/MSE-541/latimes-index";
    private static final String GzipPath = "/Users/victorsu/Desktop/MSE-541/latimes.gz";
    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
        "Dr.", "Mr.", "Mrs.", "Ms.", "Inc.", "U.S.", "e.g.", "i.e.", "etc.", "Jr.", "Sr.", "Prof.", "Rev."));

    public static void main(String[] args) throws FileNotFoundException, IOException {
        System.out.println("Starting to load the inverted index into memmory");
        //read the index
        invertedIndex = loadInvertedIndex(indexDirectory);
        //read the lexicon
        lexicon = loadLexicon(indexDirectory);
        // read all metadata
        documentMetadata = loadDocumentMetadata(indexDirectory);
        // doc lengths
        docLengths = loadDocLengths(docLengthsFile);
        // total docs
        totalDocs = documentMetadata.size();
        // avg doc lengths
        avgDocLength = loadAverageDocLengths();
        // read documents from gzip
        documents = new HashMap<>();
        processGZippedfiles(GzipPath);

        System.out.println("Finished Loading the Index!");
        performRetrieval();
    }

    public static void performRetrieval(){

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Enter your query (or type Q to quit): ");
            String query = scanner.nextLine();

            if (query.equalsIgnoreCase("Q")) {
                System.out.println("Goodbye!");
                break;
            }

            // Perform BM25 retrieval and rank results
            long startTime = System.nanoTime();
            List<String> top10Docs = generateBM25Results(query);
            long endTime = System.nanoTime();

            displayResults(top10Docs, query);

            // Display retrieval time
            System.out.println("Retrieval took " + String.format("%.2f", (endTime - startTime) / 1e9) + " seconds.");

            // Post-retrieval interaction
            while (true) {
                System.out.print("Type the rank of a document to view, 'N' for new query, or 'Q' to quit: ");
                String input = scanner.nextLine();

                if (input.equalsIgnoreCase("N")) break;
                if (input.equalsIgnoreCase("Q")) {
                    System.out.println("Goodbye!");
                    return;
                }

                try {
                    int rank = Integer.parseInt(input);
                    if (rank >= 1 && rank <= 10) {
                        System.out.println("--------------------------------------------Printing Document--------------------------------------------");
                        System.out.println();
                        getDoc(indexDirectory, "docno", top10Docs.get(rank-1));
                    } else {
                        System.out.println("Invalid rank. Please try again.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please try again.");
                }
            }
        }
        scanner.close();
    }

    private static void displayResults(List<String> top10Docs, String query) {
        int rank = 1;
        for (String docNo : top10Docs) {
            String headline = documents.get(docNo).getHeadline();
            String date = documents.get(docNo).getDate();

            String qbs = getQbs(documents.get(docNo).getSentences(), query);

            if (headline == null || headline.isEmpty()) {
                headline = qbs.length() > 50 ? qbs.substring(0, 50) + "..." : qbs;
            }

            System.out.println(rank++ + ". " + headline + " (" + date.replaceAll("\\s+", " ").trim()+ ")");
            System.out.println(qbs + " (" + docNo + ")");
            System.out.println();
        }
    }

    private static String getQbs(List<String> sentences, String query) {
        double k1 = 1.2;
        double b = 0.75;
        List<String> queryTokens = splitQueryIntoTokens(query);
    
        Map<String, Double> sentenceScores = new HashMap<>();
        for (String sentence : sentences) {
            double score = calculateSentenceBM25Score(sentence, queryTokens, sentences, k1, b);
            sentenceScores.put(sentence, score);
        }
    
        // Rank sentences by score
        List<Map.Entry<String, Double>> rankedSentences = sentenceScores.entrySet().stream()
            .sorted((a, b1) -> Double.compare(b1.getValue(), a.getValue()))
            .collect(Collectors.toList());
    
        // Combine top 2-3 sentences into a summary
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < Math.min(2, rankedSentences.size()); i++) {
            String sentence = rankedSentences.get(i).getKey();

            if(sentence.startsWith("\"") && !sentence.endsWith("\"")){
                sentence += "\"";  // Add an ending quote if it's missing
            }
            summary.append(rankedSentences.get(i).getKey()).append(" ");
        }
    
        return summary.toString().trim();
    }
    
    

    private static double calculateSentenceBM25Score(String sentence, List<String> queryTokens, List<String> allSentences, double k1, double b) {
        // Tokenize the sentence and calculate term frequencies
        List<String> words = splitQueryIntoTokens(sentence);
        Map<String, Integer> termFreq = new HashMap<>();
        for (String word : words) {
            termFreq.put(word, termFreq.getOrDefault(word, 0) + 1);
        }
    
        // Calculate average sentence length
        int totalLength = 0;
        for (String s : allSentences) {
            totalLength += splitQueryIntoTokens(s).size();
        }
        double avgSentenceLength = totalLength / (double) allSentences.size();
    
        double sentenceLength = words.size();
        double score = 0.0;
    
        // Calculate BM25 score
        for (String queryToken : queryTokens) {
            int f = termFreq.getOrDefault(queryToken, 0);
            int n = 0;
    
            // Count the number of sentences containing the query token
            for (String s : allSentences) {
                if (splitQueryIntoTokens(s).contains(queryToken)) {
                    n++;
                }
            }
    
            double idf = Math.log((allSentences.size() - n + 0.5) / (n + 0.5) + 1);
            score += idf * ((f * (k1 + 1)) / (f + k1 * (1 - b + b * (sentenceLength / avgSentenceLength))));
        }
    
        return score;
    }
    

    private static List<String> extractSentences(StringBuffer sb) {
        List<String> sentences = new ArrayList<>();
    
        Pattern textPattern = Pattern.compile("<TEXT>(.*?)</TEXT>", Pattern.DOTALL);
        Matcher textMatcher = textPattern.matcher(sb);
    
        if (textMatcher.find()) {
            String textContent = textMatcher.group(1);
            extractSentencesFromContent(sentences, textContent);
        }
    
        Pattern graphicPattern = Pattern.compile("<GRAPHIC>(.*?)</GRAPHIC>", Pattern.DOTALL);
        Matcher graphicMatcher = graphicPattern.matcher(sb);
    
        while (graphicMatcher.find()) {
            String graphicContent = graphicMatcher.group(1);
            extractSentencesFromContent(sentences, graphicContent);
        }
    
        return sentences;
    }
    
    // Helper method to extract sentences from a given content
    private static void extractSentencesFromContent(List<String> sentences, String content) {
        content = content.replaceAll("\\s+", " ").trim();
    
        Pattern paragraphPattern = Pattern.compile("<P>(.*?)</P>", Pattern.DOTALL);
        Matcher paragraphMatcher = paragraphPattern.matcher(content);
    
        while (paragraphMatcher.find()) {
            String paragraph = paragraphMatcher.group(1).trim();
            if (!paragraph.isEmpty()) {
                String[] rawSentences = splitSentences(paragraph);
                for (String sentence : rawSentences) {
                    sentences.add(sentence.trim()); // Trim any leading/trailing spaces
                }
            }
        }
    }
    

    private static String[] splitSentences(String paragraph) {
        List<String> sentences = new ArrayList<>();
        StringBuilder currentSentence = new StringBuilder();

        // Split by sentence-ending punctuation marks (period, exclamation, question mark)
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            currentSentence.append(c);

            // Check if the current character is sentence-ending punctuation
            if (c == '.' || c == '!' || c == '?') {
                // Look ahead to see if we should split the sentence
                if (i + 1 < paragraph.length() && Character.isWhitespace(paragraph.charAt(i + 1))) {
                    // Look ahead to see if the sentence ends with an abbreviation
                    String sentence = currentSentence.toString().trim();
                    if (!isAbbreviation(sentence)) {
                        sentences.add(sentence);
                        currentSentence = new StringBuilder();  // Reset for next sentence
                    }
                }
            }
        }

        // Add the remaining sentence if there is any content
        if (currentSentence.length() > 0) {
            sentences.add(currentSentence.toString().trim());
        }

        return sentences.toArray(new String[0]);
    }

    // Check if the sentence ends with an abbreviation
    private static boolean isAbbreviation(String sentence) {
        for (String abbreviation : ABBREVIATIONS) {
            if (sentence.endsWith(abbreviation)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> loadLexicon(String indexPath) {
        Map<String, Integer> lexicon = new HashMap<>();
        
        String lexiconPath = "/lexicon/lexicon.txt";
    
        try (BufferedReader reader = new BufferedReader(new FileReader(indexPath + lexiconPath))) {
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
        String invertedIndexPath = "/invertedIndex/invertedIndex.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(indexPath + invertedIndexPath))) {
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

    public static void processGZippedfiles(String inputPath) throws FileNotFoundException, IOException{
        try{
            InputStream fileStream = new FileInputStream(inputPath);
            InputStream gzipStream = new GZIPInputStream(fileStream);
            Reader decoder = new InputStreamReader(gzipStream);
            BufferedReader buffered = new BufferedReader(decoder);
            processFiles(buffered);
        }catch(FileNotFoundException e){
            System.out.println(e);
        }catch(IOException e){
            System.out.println(e);
        }
    }

    private static void processFiles(BufferedReader buffered) throws IOException {
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
                    // String documentContents = removeAllTags(sb);
                    extractAndMapDoc(sb, date, headline, docNo);
                    internalId++;
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

    private static List<String> generateBM25Results(String query) {
        List<String> results = new ArrayList<>();
    
        List<String> queryTokens = splitQueryIntoTokens(query);
    
        Map<Integer, Double> scores = calculateBM25(queryTokens);
    
        List<Map.Entry<Integer, Double>> rankedDocs = scores.entrySet().stream()
            .sorted((a, b) -> {
                int cmp = Double.compare(b.getValue(), a.getValue());
                if (cmp != 0) return cmp;
                // Tie-break by docNo lexicographically
                return documentMetadata.get(a.getKey()).getDocNo()
                        .compareTo(documentMetadata.get(b.getKey()).getDocNo());
            })
            .limit(10)
            .collect(Collectors.toList());
    
        for (Map.Entry<Integer, Double> entry : rankedDocs) {
            String docNo = documentMetadata.get(entry.getKey()).getDocNo();
            results.add(docNo);
        }
    
        return results;
    }

    private static Map<Integer, Double> calculateBM25(List<String> queryTokens){
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

    private static void extractAndMapDoc(StringBuffer sb, String date, String headline, String docNo){
        Document doc = new Document(docNo, extractHeadline(sb), extractSentences(sb), date);
        documents.put(docNo, doc);
    }

    private static String extractHeadline(StringBuffer sb){
        StringBuilder headline = new StringBuilder();

        Pattern headlinePattern = Pattern.compile("<HEADLINE>(.*?)</HEADLINE>", Pattern.DOTALL);
        Matcher headlineMatcher = headlinePattern.matcher(sb);

        if (headlineMatcher.find()) {
            String headlineContent = headlineMatcher.group(1);

            Pattern paragraphPattern = Pattern.compile("<P>(.*?)</P>", Pattern.DOTALL);
            Matcher paragraphMatcher = paragraphPattern.matcher(headlineContent);

            while (paragraphMatcher.find()) {
                String part = paragraphMatcher.group(1).trim();
                if (!part.isEmpty()) {
                    headline.append(part).append(" ");
                }
            }
        }
        return headline.toString().trim(); // Return the concatenated headline
    }

    // Extract document number
    private static String getDocNo(String line) {
        return line.replaceAll("<DOCNO>", "").replaceAll("</DOCNO>", "").trim();
    }

    // Extract content up to the second comma (for date extraction)
    private static String getUpToSecondComma(String line) {
        String[] parts = line.split(",");
        if (parts.length >= 2) {
            return parts[0] + ", " + parts[1];
        }
        return line;
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


    /*
     * FOR READING THE DOCUMENT FROM THE INDEX
     * 
     */
    private static void getDoc(String path, String type, String identifier){
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
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath + "/docnos.txt"))) {
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
        String metadataPath = path + "/metadata/" + identifier + "-metadata.txt";

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
        String metadataPath = path + "/metadata/" + docNo + "-metadata.txt";

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

class Document {
    private String docNo;
    private String headline;
    private List<String> sentences;
    private String date;

    // Constructor
    public Document(String docNo, String headline, List<String> sentences, String date) {
        this.docNo = docNo;
        this.headline = headline;
        this.sentences = sentences;
        this.date = date;
    }

    public String getDocNo() {
        return docNo;
    }

    public String getHeadline() {
        return headline;
    }

    public List<String> getSentences() {
        return sentences;
    }

    public String getDate() {
        return date;
    }

    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public void setSentences(List<String> sentences) {
        this.sentences = sentences;
    }

    public void setDate(String date) {
        this.date = date;
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