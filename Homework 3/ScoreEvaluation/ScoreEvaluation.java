package ScoreEvaluation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ScoreEvaluation {
    private static List<QrelsEntry> qrels;
    private static List<ResultEntry> results;
    public static void main(String[] args) {
        if(args.length != 3){
            System.out.println("Please provide both arguements when running the program");
            System.exit(1);
        }

        File resultsFile = new File(args[0]);
        File qrelsFile = new File(args[1]);
        String evaluation = args[2];
        qrels = new ArrayList<QrelsEntry>();
        results = new ArrayList<ResultEntry>();

        loadQrels(qrelsFile.toString());
        loadResults(resultsFile.toString());
        calculateScores(evaluation);
    }

    public static void loadResults(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length < 6) {
                    continue;
                }

                int topicId = Integer.parseInt(parts[0]); 
                String q = parts[1];                    
                String docId = parts[2];                  
                int rank = Integer.parseInt(parts[3]);      
                double score = Double.parseDouble(parts[4]);
                String runID = parts[5];                     

                ResultEntry entry = new ResultEntry(topicId, q, docId, rank, score, runID);
                results.add(entry);
            }
        } catch (NumberFormatException e){
            System.err.println("Error reading results file, improper format ");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error reading results file");
            System.exit(1);
        }
    }

    public static void loadQrels(String filePath) {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");

                if (parts.length < 4) {
                    continue;
                }

                int topicId = Integer.parseInt(parts[0]); 
                String docId = parts[2];               
                int judgment = Integer.parseInt(parts[3]);

                QrelsEntry entry = new QrelsEntry(topicId, docId, judgment);
                qrels.add(entry);
            }
        } catch (IOException e) {
            System.err.println("Error reading qrels file: " + e.getMessage());
        }
    }

    private static void calculateScores(String evaluationPath) {
        File file = new File(evaluationPath);
    
        file.getParentFile().mkdirs();
    
        Map<Integer, Map<String, Integer>> relevanceMap = buildRelevanceMap();
    
        // Collect scores in separate lists
        List<String> apScores = new ArrayList<>();
        List<String> ndcgAt10Scores = new ArrayList<>();
        List<String> ndcgAt1000Scores = new ArrayList<>();
        List<String> precisionScores = new ArrayList<>();
    
        // Calculate scores for each topic and collect them
        for (Map.Entry<Integer, Map<String, Integer>> entry : relevanceMap.entrySet()) {
            int topicId = entry.getKey();
            Map<String, Integer> relMap = entry.getValue();
    
            List<ResultEntry> topicResults = getTopicResults(topicId);
    
            // Calculate each score
            double precisionAt10 = calculatePrecisionAtK(topicResults, relMap, 10);
            double averagePrecision = calculateAveragePrecision(topicResults, relMap);
            double ndcgAt10 = calculateNDCG(topicResults, relMap, 10);
            double ndcgAt1000 = calculateNDCG(topicResults, relMap, 1000);
    
            apScores.add(formatScore("ap", topicId, averagePrecision));
            ndcgAt10Scores.add(formatScore("ndcg_cut_10", topicId, ndcgAt10));
            ndcgAt1000Scores.add(formatScore("ndcg_cut_1000", topicId, ndcgAt1000));
            precisionScores.add(formatScore("P_10", topicId, precisionAt10));
        }
    
        writeScoresToFile(evaluationPath, apScores, ndcgAt10Scores, ndcgAt1000Scores, precisionScores);
        System.out.println("Scores written");
    }

    private static Map<Integer, Map<String, Integer>> buildRelevanceMap() {
        Map<Integer, Map<String, Integer>> relevanceMap = new TreeMap<>();
        // Each topicID has an associated map, storing docno and judgment
        for (QrelsEntry qrelsEntry : qrels) {
            relevanceMap
                .computeIfAbsent(qrelsEntry.getTopicID(), k -> new HashMap<>())
                .put(qrelsEntry.getDocno(), qrelsEntry.getJudgment());
        }
        return relevanceMap;
    }

    private static List<ResultEntry> getTopicResults(int topicId) {
        List<ResultEntry> topicResults = new ArrayList<>();
        for (ResultEntry result : results) {
            if (result.getTopicID() == topicId) {
                topicResults.add(result);
            }
        }
        return topicResults;
    }
    
    private static String formatScore(String scoreType, int topicId, double scoreValue) {
        return String.format("%-20s\t%d\t%.4f", scoreType, topicId, scoreValue);
    }
    
    private static void writeScoresToFile(String filePath, List<String> apScores, List<String> ndcgAt10Scores, List<String> ndcgAt1000Scores, List<String> precisionScores) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String score : apScores) {
                writer.write(score);
                writer.newLine();
            }
    
            for (String score : ndcgAt10Scores) {
                writer.write(score);
                writer.newLine();
            }

            for (String score : ndcgAt1000Scores) {
                writer.write(score);
                writer.newLine();
            }
    
            for (String score : precisionScores) {
                writer.write(score);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    public static double calculateAveragePrecision(List<ResultEntry> results, Map<String, Integer> relevanceMap) {
        int relevantCount = 0;
        double sumPrecision = 0.0;
    
        for (int i = 0; i < results.size(); i++) {
            ResultEntry result = results.get(i);
            
            if (relevanceMap.containsKey(result.getDocNo()) && relevanceMap.get(result.getDocNo()) > 0) {
                relevantCount++;
                double precisionAtK = (double) relevantCount / (i + 1);
                sumPrecision += precisionAtK;
            }
        }
    
        int totalRelevantItems = (int) relevanceMap.values().stream().filter(judgment -> judgment > 0).count();
        return sumPrecision / totalRelevantItems;
    }

    private static double calculatePrecisionAtK(List<ResultEntry> results, Map<String, Integer> relevanceMap, int k) {
        int relevantCount = 0;
        for (int i = 0; i < Math.min(k, results.size()); i++) {
            if (relevanceMap.containsKey(results.get(i).getDocNo()) && relevanceMap.get(results.get(i).getDocNo()) > 0) {
                relevantCount++;
            }
        }
        return (double) relevantCount / k;
    }

    private static double calculateNDCG(List<ResultEntry> results, Map<String, Integer> relevanceMap, int k) {
        double dcg = 0.0;
        double idcg = 0.0;

        List<Integer> relevanceList = new ArrayList<>(relevanceMap.values());
        relevanceList.sort(Collections.reverseOrder());

        //dcg
        for (int i = 0; i < Math.min(k, results.size()); i++) {
            ResultEntry result = results.get(i);
            int relevance = relevanceMap.getOrDefault(result.getDocNo(), 0);
            double gain = 0.0;
            if(relevance > 0){
                gain = 1.0;
            }
            double discount = Math.log(i + 2) / Math.log(2);
            dcg += gain / discount;
        }

        //idcg
        for (int i = 0; i < Math.min(k, relevanceList.size()); i++) {
            int idealRelevance = relevanceList.get(i);
            double idealGain = 0.0;
            if(idealRelevance == 1){
                idealGain = 1.0;
            }
            double discount = Math.log(i + 2) / Math.log(2);
            idcg += idealGain / discount;
        }

        if(idcg == 0){
            return 0.0;
        }

        return dcg / idcg;
    }
}

class QrelsEntry {
    private int topicID;
    private String docno;
    private int judgment;

    public QrelsEntry(int topicID, String docno, int judgment) {
        this.topicID = topicID;
        this.docno = docno;
        this.judgment = judgment;
    }
    public int getTopicID() {
        return topicID;
    }

    public String getDocno() {
        return docno;
    }

    public int getJudgment() {
        return judgment;
    }

    @Override
    public String toString() {
        return topicID + " " + docno + " " + judgment;
    }
}

// 6, 11, 13 documents are in the wrong format

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

    public String getDocNo(){
        return docNo;
    }

    public String toString() {
        return topicID + " " + q0 + " " + docNo + " " + rank + " " + score + " " + runTag;
    }

}

