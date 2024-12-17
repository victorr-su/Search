# MSE 541 Assignment 4 - Victor Su (v3su) - 20943854

This program calculates the BM25 given an inverted index and a lexicon. This index can be stemmed, or just baseline. This is specified in the program's arguements. To achieve the stemmed index and lexicon, run IndexEngine with the arguement "stem", otherwise run it with "baseline". Similar for BM25, to run it using the stemmed index and queries, run it with the arguement "stem", else "baseline". Follow the commands below:

## BM25

The BM 25 program takes in 5 arguements.

1. Index Directory
2. Queries file
3. DocLengths file
4. Output file
5. baseline or stem

The queries file and the doc lengths file can be found under the latimes-index after running the Index Engine

```bash
java BM25/BM25.java <Index Directory> <Queries File> <Doc Lengths File> <Output File> <baseline or stem>
```

Example for stem:

```bash
java BM25/BM25.java "/Users/victorsu/Desktop/MSE-541/latimes-index" "/Users/victorsu/Desktop/MSE-541/mse-541-f24-hw4-victorr-su/Queries/queries.txt" "/Users/victorsu/Desktop/MSE-541/latimes-index/doc-lengths/doc-lengths.txt" "hw4-bm25-stem-v3su.txt" "stem"
```

Example for baseline:

```bash
java BM25/BM25.java "/Users/victorsu/Desktop/MSE-541/latimes-index" "/Users/victorsu/Desktop/MSE-541/mse-541-f24-hw4-victorr-su/Queries/queries.txt" "/Users/victorsu/Desktop/MSE-541/latimes-index/doc-lengths/doc-lengths.txt" "hw4-bm25-baseline-v3su.txt" "baseline"
```

## Index Engine

The respective index will be in the latimes-index under the path invertedIndex/ and lexicon/

```bash
java IndexEngine/IndexEngine.java <Path to latimes.gz> <Path latimes-index> <stem or baseline>
```

Example for stem:

```bash
java IndexEngine/IndexEngine.java "/Users/victorsu/Desktop/MSE-541/latimes.gz" "/Users/victorsu/Desktop/MSE-541/latimes-index" "stem"
```

Example for baseline

```bash
java IndexEngine/IndexEngine.java "/Users/victorsu/Desktop/MSE-541/latimes.gz" "/Users/victorsu/Desktop/MSE-541/latimes-index" "baseline"
```
