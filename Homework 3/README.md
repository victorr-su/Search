# MSE 541 Assignment 3 - Victor Su (v3su) - 20943854

This program calculates the average precision, precision@10, NDCG@10, and NDCG@1000 based on a qrels file and a results file. The results of each of the runs should appear under the third arguement specified in your command. In order to run the program on different results files, follow the steps below.

## Steps to run

### 1. Determine the location of the results file, the qrels file, and the path and filename that you want your scores to go into.

### 2. Run the follow command into the terminal

```bash
java ScoreEvaluation/ScoreEvaluation.java <Path to results file> <Path to qrels file> <Output path>
```

#### Example commands for results file 1 and results file 2

For results file 1

```bash
java ScoreEvaluation/ScoreEvaluation.java "/Users/victorsu/Desktop/MSE-541/mse-541-f24-hw3-victorr-su/ContextFiles/results-files/student1.results" "/Users/victorsu/Desktop/MSE-541/mse-541-f24-hw3-victorr-su/ContextFiles/qrels/LA-only.trec8-401.450.minus416-423-437-444-447.txt" "Evaluation/student1_scores.txt"
```

For results file 2

```bash
java ScoreEvaluation/ScoreEvaluation.java "/Users/victorsu/Desktop/MSE-541/mse-541-f24-hw3-victorr-su/ContextFiles/results-files/student2.results" "/Users/victorsu/Desktop/MSE-541/mse-541-f24-hw3-victorr-su/ContextFiles/qrels/LA-only.trec8-401.450.minus416-423-437-444-447.txt" "Evaluation/student1_scores.txt"
```

etc.
