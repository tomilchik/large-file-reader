package com.idt.codechallenge;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * <pre>
 * This class implements an algorithm for matching data records against set of queries.
 * For full description of the functionality see comments for method {@link #match(OutputStream) match}.
 * 
 * To run from CLI use {@link com.idt.codechallenge.MatcherRunner MatcherRunner}.
 * 
 * Instances of this class are thread-safe: no instance variables are changed outside of constructor.
 * </pre>		
 * @author leonidtomilchik
 *
 */
public class RecordMatcher implements Matcher {
	
	// some defaults for RecordMatcher initializer
	private final static double DEFAULT_MEMORY_RATIO = 0.5d;
	private final static boolean DEFAULT_VERBOSE = false;
	private final static boolean DEFAULT_SUPERVERBOSE = false;
	private final static int DEFAULT_BUFFERSIZE = 8192;
	private final static boolean DEFAULT_OPTDATAREADS = false;
	private final static boolean DEFAULT_OPTQUERYREADS = false;

	private final String dataFileLocation;
	private final String queryFileLocation;
	
	private boolean isOptimizeDataReads = DEFAULT_OPTDATAREADS;
	private boolean isOptimizeQueryReads = DEFAULT_OPTQUERYREADS;
	private boolean isVerbose = DEFAULT_VERBOSE;
	private boolean isSuperVerbose = DEFAULT_SUPERVERBOSE;
	private double minFreeMemoryRatio = DEFAULT_MEMORY_RATIO;
	private int bufferSize = DEFAULT_BUFFERSIZE;
	
	/**
	 * Ctor with all defaults except for data and query files locations.
	 * 
	 * @param dataFileLocation data file: full path or URL
	 * @param queryFileLocation query file: full path or URL
	 * @throws FileNotFoundException If any of the input files/URL's are not valid or not reachable.
	 */
	public RecordMatcher(String dataFileLocation, String queryFileLocation) throws FileNotFoundException {
		this(dataFileLocation, queryFileLocation, DEFAULT_MEMORY_RATIO, DEFAULT_BUFFERSIZE, DEFAULT_OPTDATAREADS, DEFAULT_OPTQUERYREADS, DEFAULT_VERBOSE, DEFAULT_SUPERVERBOSE);
	}

	/**
	 * <pre>
	 * Ctor with full set of arguments.
	 * 
	 * Performance-affecting factors:
	 * Combination of minMemRatio, bufSize, isOptDataReads, isOptQryReads, actual memory available on the machine, 
	 * locations of data and query files (local vs remote) may have noticeable effect on performance. 
	 * 
	 * Defaults:
	 * - 8192 buffer size (Java default for BufferedReader);
	 * - try to pre-load full query file into memory;
	 * - do not pre-load data file; go with whatever optimization BufferedReader applies;
	 * - memory ratio (only has an effect when pre-loading either queries or data, or both) of 0.5.
	 * This, depending on combination of factors, may have very serious performance impact.
	 * If pre-loading is ON - this class will watch remaining available memory while pre-loading, 
	 * and will stop the pre-load once free-to-available goes below desired threshold, and switch back to row-at-a-time.
	 * If this happens around time when we are reading data row #999,999 out of 1,000,000 - we will have wasted all this time, 
	 * and will go back to loading the 1M rows again, but this time row-at-a-time.
	 * 
	 * Experiment with the combinations!
	 * 
	 * This impl does not do one seemingly performance-enhancing thing: no multi-threaded reads - here's why:
	 * - threads improve performance for sure if you have multiple cores; if you don't - then it depends (read on);
	 * - multi-threading disk I/O may actually make things worse if underlying storage is physical - the physical disk reader will be jumping back-n-forth more;
	 * - if files are remote - it is still unknown how they are stored on the other end;
	 * - multi-threading brings in the overhead for context-switching etc.
	 * 
	 * Separate experiments with reading a 1M file (no extra processing, just reads) shown that statistically
	 * having ~10 reader threads results in 10% performance gain vs 1 thread. Not really worth it.
	 * 
	 * </pre>
	 * 
	 * @param dfleLocation file path or URL of the data file
	 * @param qfLocation file path or URL of the query file
	 * @param minMemRatio a base-1, positive double. Minimal memory ratio to maintain while attempting to read all of the query file into memory. If null is passed - will use default: 0.5.
	 * @param bufSize positive integer - size of BuffereReader's buffers for reading data and query files. If null is passed - will use default 8192.
	 * @param isOptDataReads when true - tries to pre-read entire data file into memory (default: false).
	 * @param isOptQryReads when true - tries to pre-read entire data query into memory (default: false).
	 * @param isVerb when true - generates some extra output
	 * @param isSuperVerb when true - generates lots of extra output
	 */
	public RecordMatcher(
			String dfleLocation, 
			String qfLocation, 
			Double  minMemRatio, 
			Integer bufSize, 
			Boolean isOptDataReads, 
			Boolean isOptQryReads, 
			Boolean isVerb, 
			Boolean isSuperVerb) {

		if (dfleLocation == null) throw new IllegalArgumentException("dataFileLocation cannot be null");
		this.dataFileLocation = dfleLocation;

		if (qfLocation == null) throw new IllegalArgumentException("queryFileLocation cannot be null");
		this.queryFileLocation = qfLocation;
		
		this.isOptimizeDataReads = (isOptDataReads != null? isOptDataReads : isOptimizeDataReads);
		this.isOptimizeQueryReads = (isOptQryReads != null? isOptQryReads : isOptimizeQueryReads);

		this.isSuperVerbose = (isSuperVerb != null? isSuperVerb : isSuperVerbose);;
		this.isVerbose = (isVerb != null?  isVerb || isSuperVerbose : isVerbose || isSuperVerbose);	// superverbose is an overriding option
		
		if (minMemRatio != null && (minMemRatio <= 0 || minMemRatio > 1)) throw new IllegalArgumentException("minFreeMemoryRatio must be a positive floating point number <= 1.0");
		this.minFreeMemoryRatio = (minMemRatio != null? minMemRatio : DEFAULT_MEMORY_RATIO);
		
		if(bufSize !=  null && bufSize < 0 ) throw new IllegalArgumentException("bufferSize must be positive. Java default is " + DEFAULT_BUFFERSIZE);
		this.bufferSize = (bufSize != null? bufSize : DEFAULT_BUFFERSIZE);
	}	

	/**
	 * <pre>
	 * This method implements the match algo.
	 * 
	 * The original task text:
	 * "Process a large data file consisting of text records where each record represents a list of words. 
	 * Given a set of words (called a query; contained in a query file) a record is considered a match if it contains all of the query words.
	 * For each query in the query file find the matching records in the data file and for each matching record outputs the number of times that each non-query word appears.
	 * For each query and matching record the output is a JSON dictionary with the non-query words and their count.
	 * 
     * For example for the following data records:
	 * red,sky,coin,bucket,chair,blue
	 * apple,chair,purple,red,house
	 * silver,blue,apple,coin,street
	 * 
	 * And the query:
	 * red,apple
	 * 
	 * The output will be:
	 * {chair : 1, purple : 1, house : 1}
	 * 
	 * The second line (record) matches the query (it contains both `apple` and `red`) so if we count all other non query words this will give us the above output.
	 * The results are printed to the standard output."
	 * 
	 * ################################################
	 * Assumptions:
	 * 
	 * - "for each query and matching record": assuming that this says that each occurrence of (record + query = match) produces a separate output line.
	 * I.e. if same query matches several rows - several output lines will be produced.
	 * E.g. if condition was "for each query and matching recordS" then the algo would have to be built differently,
	 * producing one output per each query, with all row matches combined in it.
	 * - match is case-sensitive (nothing was mentioned in the task)
	 * 
	 * ################################################
	 * Implementation considerations:
	 * 
	 * - which list is likely to be larger? Coz that's the one we want to iterate only once - hence it needs to be in the outer iteration 
	 * If any list lives behind a remote protocol - cannot tell its size upfront, and cannot compare sizes.
	 * 
	 * - each line in the output must include parts dependent on all queries in the query list.  
	 * This makes query list a better candidate for the inner iteration (which will be repeated many times).
	 * If it is not so - then we'll have to collect all results until all iterations are done, running the risk of collecting too much - and out-of-memory.
	 * 
	 * If we can pre-guess how large the query and/or data files are - may try to pre-load either of those into memory first.
	 * To prevent OOM: start reading it in all at once, keep checking memory.
	 * If memory getting too tight (default - 0.5 of total available to JVM): 
	 * then we'll have to sacrifice performance and revert to re-reading files.
	 * 
	 * This algo will perform extremely poorly when queries are read line-by-line. This may happen when:
	 * - either query file is substantially large - nothing we can do;
	 * - or query file is small enough to fit into memory, but the reader was created with query optimization OFF.
	 * 
	 * You may want to experiment with combinations of bufferSize and minFreeMemoryRatio.
	 * </pre>
	 * 
	 * @param out an OutpuStream to write result into. 
	 * @throws IOException some Readers managed outside try-with-resources may throw.
	 * @return number of matches found
	 */
	@Override
	public long match(OutputStream out) throws IOException {

		if (isVerbose) {
			// show memory stats before processing
			Utils.getEstimatedMemoryRatio(isVerbose);
		}

		// re-doing the check done in constructor:
		// since this instance was created the file names may have become invalid.
		// the purpose is to throw if names are no longer valid.
		Utils.getFileLocationType(dataFileLocation);
		Utils.getFileLocationType(queryFileLocation);

		// to write output to
		BufferedWriter outWriter = new BufferedWriter(new OutputStreamWriter(out));
		
		// Gson instances are reusable; use this one each time JSON is needed
		Gson gson = new Gson();
		
		// running row counter, for inclusion into results, and stats to display
		long rowNum = 0;
		long matchCount = 0;
		
		try (
				// the lambda is pass-through: data line in, data line out.
				OptimizingBufferedReader<String> brData = new OptimizingBufferedReader<String>(
						dataFileLocation,
						isOptimizeDataReads,
						minFreeMemoryRatio,
						bufferSize,
						(s) -> {return s;}) ;
				// the lambda will split each query into a Set containing unique query words.
				OptimizingBufferedReader<Set<String>> brQuery = new OptimizingBufferedReader<Set<String>>(
						queryFileLocation,
						isOptimizeQueryReads,
						minFreeMemoryRatio,
						bufferSize,
						(s) -> {return Arrays.asList(s.split(",")).stream().collect(Collectors.toSet());});
			) {
			
			// refillable map of word frequencies, per line per query... 
			Map<String, Integer> wordCounts = null;	
			
			String line = null;

			// data file can be very large: always reading it from the Reader one line at a time	
			while ((line = brData.readLine()) != null) {
				rowNum++;
				
				// tokenize the line, count word frequencies in it, store for subsequent matching against all queries
		        wordCounts = 
		        		Arrays.asList(line.split(","))
		        		.stream()
		                .collect(
		                		Collectors.toMap(w -> w, w -> 1, Integer::sum)
		                		);
		        if (isSuperVerbose) System.out.println("row: "+rowNum + "; wordCounts="+wordCounts);

		        // apply all queries in turn to this line, write out the result (if any)
		        matchCount += writeOutOnlyNonMatchingWords(brQuery, wordCounts, outWriter, gson, rowNum);

				if (isVerbose) {
					// every now and then put something out to console
					if (rowNum%50000 == 0) {
						System.out.println("rows processed: " + rowNum);
					}
				}
			}

			// and before we go: ensure that all data written into buffer is actually pushed to the stream behind writer
	        if (isVerbose) System.out.println("Output:");
			outWriter.flush();
			
		}
		catch (Exception e) {
			throw new RuntimeException("Error during matching. ", e);
		}
			
		if (isVerbose) {
			// just to show memory stats after processing
			Utils.getEstimatedMemoryRatio(isVerbose);
			System.out.println("Records processed: " + rowNum);
		}
		
		return matchCount;		
	}

	/**
	 * Matches a passed map of {word, count} against all queries. 
	 * Queries are read from the passed reader; each query is a list of query words: {qword1, qword2, qword3,...}.
	 * Prints out (to the passed writer) only {word,count} pairs from the map where word does not match any of the query words.
	 * 
	 * @param reader reader to write result to
	 * @param wordCounts map of {word,count}
	 * @param outWriter BufferedWriter to write result to
	 * @param gson reusable instance of Gson to construct JSON
	 * @param row row in the data file from which the map of {word,count} pairs was read
	 * @throws IOException passes on whatever reader throws
	 */
	private long writeOutOnlyNonMatchingWords(
			OptimizingBufferedReader<Set<String>> reader,
			Map<String, Integer> wordCounts,
			BufferedWriter outWriter,
			Gson gson,
			long rowNum) throws IOException {

		long matchCount = 0;
		// a holder for a filtered version of the wordCounts Map - only non-query words
		Map<String, Integer> result = null;	
		
		// apply all queries in turn to this map of word counts
		Set<String> queryWords;
        while ((queryWords = reader.readLine()) != null) {	
        	
        	Set<String> qw = queryWords;	// the lambda below complains and wants this var to be either "in scope", or final. Re-assignment does it
	        if (isSuperVerbose) System.out.println("queryWords="+qw);

			// check if all query words are found in the data line
			if (wordCounts.keySet().containsAll(queryWords)) {
				if (isSuperVerbose) System.out.println("MATCH!");
				
				// ALL query criteria are found; leave only words that *do not match*, and print what's left.						
				// This stream operation filters out entries that match query
				result = 
						wordCounts.entrySet().stream()
						.filter(entry -> !qw.contains(entry.getKey()))
						.collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));						
			
				// this output adds line # and the query to the match result
				JsonObject json = new JsonObject();
				json.addProperty("line", new Long(rowNum));
				json.addProperty("query", queryWords.toString());
				json.add("result", gson.toJsonTree(result));
				
				// write out a result of all queries that ran against this data line
				outWriter.write(json.toString());
				outWriter.newLine();
				
				matchCount++;
			}
        }
        
        return matchCount;
	}
}
