# large-file-reader
Utility to read and process large data files with queries

The utility processes a large data file consisting of text records where each record represents a list of words. 
Given a set of words (called a query; contained in a query file) a record is considered a match if it contains all of the query words.
For each query in the query file the utility finds the matching records in the data file and for each matching record outputs the number of times that each non-query word appears.
For each query and matching record the output is a JSON dictionary with the non-query words and their count.

For example for the following data records:
red,sky,coin,bucket,chair,blue
apple,chair,purple,red,house
silver,blue,apple,coin,street

And the query:
red,apple

The output will be:
{chair : 1, purple : 1, house : 1}

The second line (record) matches the query (it contains both `apple` and `red`) so if we count all other non query words this will give us the above output.
The results are printed to the standard output.
