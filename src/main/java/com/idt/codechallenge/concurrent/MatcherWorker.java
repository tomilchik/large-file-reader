package com.idt.codechallenge.concurrent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * A worker is meant to be started in a thread and keep reading data lines from DataReader,
 * matching them against queries from QueryHolder, and outputting the results to the OutputStream.
 */
class MatcherWorker implements Callable<Long> {
	
	private DataReader dataReader;
	private QueryHolder queryHolder;
	private boolean isVerbose;
	private boolean isSuperVerbose;
	private OutputStream out;
	
	MatcherWorker(DataReader dr, QueryHolder qh, OutputStream o, boolean v, boolean vv) {
		dataReader = dr;
		queryHolder = qh;
		isVerbose = v;
		isSuperVerbose = vv;
		out = o;
	}

	/**
	 * Runs the match algo - same as implemented in com.idt.codechallenge.RecordMatcher.
	 */
	@Override
	public Long call() {
		
		info("Worker STARTED...");
		
		String line = null;
		long rowCount = 0;
		long matchCount = 0;

		// refillable map of word frequencies, per line per query... 
		Map<String, Integer> wordCounts = null;	

		// to write output to
		BufferedWriter outWriter = new BufferedWriter(new OutputStreamWriter(out));
		
		// Gson instances are reusable; use this one each time JSON is needed
		Gson gson = new Gson();

		try {
			// read until the reader says "no more data"	
			while (dataReader.willHaveMore()) {
				while ((line = dataReader.readLine()) != null) {
					rowCount++;
					
					// tokenize the line, count word frequencies in it, store for subsequent matching against all queries
			        wordCounts = 
			        		Arrays.asList(line.split(","))
			        		.stream()
			                .collect(
			                		Collectors.toMap(w -> w, w -> 1, Integer::sum)
			                		);
			        debug(" row: "+rowCount + "; wordCounts="+wordCounts);
		
			        // apply all queries in turn to this line, write out the result (if any)
			        matchCount += writeOutOnlyNonMatchingWords(wordCounts, outWriter, gson, rowCount);
		
					if (isVerbose) {
						// every now and then put something out to console
						if (rowCount%50000 == 0) {
							debug("rows processed: " + rowCount);
						}
					}
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException("Error running match; row: "+rowCount + "; matchCount: " + matchCount + "; data: " + line, e);
		}
		
		return rowCount;
	}

	/**
	 * Matches a passed map of {word, count} against all queries. 
	 * Queries are read from the passed reader; each query is a list of query words: {qword1, qword2, qword3,...}.
	 * Prints out (to the passed writer) only {word,count} pairs from the map where word does not match any of the query words.
	 * 
\	 * @param wordCounts map of {word,count}
	 * @param outWriter BufferedWriter to write result to
	 * @param gson reusable instance of Gson to construct JSON
	 * @param row row in the data file from which the map of {word,count} pairs was read
	 * @throws IOException passes on whatever reader throws
	 */
	private long writeOutOnlyNonMatchingWords(
			Map<String, Integer> wordCounts,
			BufferedWriter outWriter,
			Gson gson,
			long rowNum) throws IOException {

		long matchCount = 0;
		// a holder for a filtered version of the wordCounts Map - only non-query words
		Map<String, Integer> result = null;	
		
		// apply all queries in turn to this map of word counts
		for (Set<String> queryWords : queryHolder.getQueries() ) {	
        	
	        debug("queryWords="+queryWords);

			// check if all query words are found in the data line
			if (wordCounts.keySet().containsAll(queryWords)) {
				debug("MATCH!");
				
				// ALL query criteria are found; leave only words that *do not match*, and print what's left.						
				// This stream operation filters out entries that match query
				result = 
						wordCounts.entrySet().stream()
						.filter(entry -> !queryWords.contains(entry.getKey()))
						.collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));						
			
				// this output adds line # and the query to the match result
				JsonObject json = new JsonObject();
				json.addProperty("line", new Long(rowNum));
				json.addProperty("query", queryWords.toString());
				json.add("result", gson.toJsonTree(result));
				
				// write out a result of all queries that ran against this data line
				outWriter.write(json.toString());
				outWriter.newLine();
				
				outWriter.flush();
				
				matchCount++;
			}
        }
        
        return matchCount;
	}

	private void info(Object message) {
		if (isVerbose) System.out.println("[" + Thread.currentThread().getName()+"] "+message);
	}
	
	private void debug(Object message) {
		if (isSuperVerbose) System.out.println("[" + Thread.currentThread().getName()+"] "+message);
	}

}
