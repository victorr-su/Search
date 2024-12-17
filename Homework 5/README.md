# MSE 541 Assignment 5 - Victor Su (v3su) - 20943854

This program calculates is able to generate an entire search experience for the user based on their own queries that they input into the system.

## How to Run

Assuming you have the invertedIndex, lexicon, and other important files inside the /latimes-index directory

```bash
java QueryBiasedSummary/QueryBiasedSummary.java
```

The program will prompt the user to enter a query, and then show the top 10 results using BM25 calculations. Once the results are shown, the user can either submit a new query, quit, or view one of the top 10 docs by entering the number in the command line.
