# MSCI 541 Assignment 1 - Victor Su 20943854

This program provides functionality to retrieve documents based on their unique identifiers and to index documents from a GZipped file. The following commands can be used to run the program:

Example path to LaTimes: "/Users/victorsu/Desktop/MSE-541/latimes-index/"

## Commands

Steps to run: Run any of the one commands below after cloning the repository. Running the index engine takes around 1.5 - 2 minutes.

Assuming you're in the directory of the repository:

1. **Index Engine**

   ```bash
    java GetDoc/GetDoc.java "<Path to LaTimes Dir>" "id" "6832"
   ```

   Example: `java IndexEngine/IndexEngine.java "/Users/victorsu/Desktop/MSE-541/latimes.gz" "/Users/victorsu/Desktop/MSE-541/LaTimes"
`

2. **Get Document by DOCNO**

   ```bash
   java GetDoc/GetDoc.java "<Path to LaTimes Dir>" "<docno OR id>" "<DocNo>"
   ```

   Example: `java GetDoc/GetDoc.java "/Users/victorsu/Desktop/MSE-541/latimes-index/" "docno" "LA010290-0030"`

3. **Get Document by Internal Id**

   ```bash
   java GetDoc/GetDoc.java "<Path to LaTimes Dir>/" "id" "6832"
   ```

   Example: `java GetDoc/GetDoc.java "/Users/victorsu/Desktop/MSE-541/latimes-index/" "id" "6832"`
