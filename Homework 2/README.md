# MSCI 541 Assignment 2 - Victor Su (v3su) 20943854

This program provides functionality to create an inverted index using the existing IndexEngine.java and to perform BooleaAND Search on the 45 queries given. Under the /ContextFiles directory, you will find all the context files needed to run the code. The following commands can be used to run the program:

Example path to LaTimes: "/Users/victorsu/Desktop/MSE-541/latimes-index/"

## Commands

Steps to run: Run any of the one commands below after cloning the repository. Running the index engine takes a few minutes (Usually 3.5 mins).

Assuming you're in the directory of the repository:

1. **Index Engine**

   ```bash
    java IndexEngine/IndexEngine.java "<Path to gzip file>" "<Path to output>"
   ```

   Example: `java IndexEngine/IndexEngine.java "/Users/victorsu/Desktop/MSE-541/latimes.gz" "/Users/victorsu/Desktop/MSE-541/LaTimes"
`

2. **BooleanAND**

   ```bash
   java BooleanAND/BooleanAND.java "<Path to latimes-index>" "<Path to the 45 queries>" "<Name of the file to output>"
   ```

   Example: `java BooleanAND/BooleanAND.java /Users/victorsu/Desktop/MSE-541/latimes-index /Users/victorsu/Desktop/MSE-541/mse-541-f24-hw2-victorr-su/IndexQueries/queries.txt  hw2-results-v3su.txt`

   The folder with all 45 queries is given in the repository under IndexQueries/queries.txt. Another file for testing purposes is also made under the same directory, under IndexQueries/test-queries.txt.

   To run a test latimes-index instead of using the entire thing, please use the file under Testing/test_input.gz
