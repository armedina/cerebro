# Cerebro
REST API that finds mutants from analyzing a DNA sequence

# Project
This project consist of java servlet, thats run in google app engine. You need to have installed the Java SE 8 Development Kit (JDK) and the Google Cloud SDK (with app-engine component) . The API store analyzed sequneces in a NOSQL data base (Google Cloud Bigtable) .

So you need to create a project in Google App Engine, and a Bigtable instance and set "projectID" and "instanceID" in appengine-web.xml to get the project work.

# Search Algorithm
The search algorithm traverses the array of strings, comparing each character c (x, y) with the character c (x-1, y) (horizontal comparison), c (x, y-1) (vertical comparison), c ( x-1, y-1) (left diagonal comparison) and c (x + 1, y-1) (right diagonal comparison), i.e.

c | c(x-1,y-1) | c(x,y-1) | c(x+1,y-1) | c
--- | --- | --- | --- |---
c | c(x-1,y) | c(x,y) | c | c

This algortihm uses a matrix (2xN) of accumulators for count the occurences of a letter in the 4 orientations . Every accumulator value occupies 2 bits for every orientation, bit7-bit-6 for horizontal, bit5-bit4 for vertical, bit3-bit2 for left diagonal and bit1-bit0 for right diagonal. When detects that c(x,y) its equals to c(x-1,y) (horizontal) increments one bit in the bit7-bit6 and check if this 2 bits are equal to 11, if this is the case, then the algorithm found a horizontal sequence. The same logic applies to the other orientations.

# Aggressive Traffic Fluctuations
One of the benefits of google app engine is dynamic scaling of app instance, according to traffic load, so the API is prepared for aggressive traffic fluctuations.

# Bigtable
The API store every sequence analyzed in the db only 1 register for each sequence. So before do the analysis the API verifies if this sequence exist in db and if does, retrives de result (if mutant or not) from the stored value, in place of analyze again the sequence with search algorithm.

Also the API , use counters (stored in db) to return stats values.

# API URL
The API have been published in googel app engine (Java Standard Enviroment), the url is https://hcjf-200618.appspot.com
To make the analysys of a secuence do a POST reques to https://hcjf-200618.appspot.com/mutant/ with a json body with the dna sequence i.e.

{"dna":["ATGCGA","CAGTGC","TTATGT","AGAAGG","CCCCTA","TCACTG"]}

To get statistics of occurences do a GET request to https://hcjf-200618.appspot.com/stats

# License
Apache 2.0
