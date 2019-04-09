package com.idt.codechallenge.concurrent;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.idt.codechallenge.Matcher;


/**
 * Data-to-query matcher that will simultaneously:
 * - read data line by line from the source;
 * - give consumers data one line at a time.
 * 
 * This in theory may improve performance of the matching process.
 * 
 * @author leonidtomilchik
 *
 */
public class ConcurrentRecordMatcher implements Matcher {
	
	private final String dataFileLocation;
	private final String queryFileLocation;
	private int poolSize;
	private int bufferSize;
	private boolean isVerbose = false;
	private boolean isSuperVerbose = false;
	
	public ConcurrentRecordMatcher(String df, String qf, Integer psize, Integer bsize, Boolean isVerb, Boolean isSuperVerb) {
		dataFileLocation = df;
		queryFileLocation = qf;
		poolSize = (psize == null? 1 : psize);
		bufferSize = (bsize == null? 8192 : bsize);
		this.isSuperVerbose = (isSuperVerb != null? isSuperVerb : isSuperVerbose);;
		this.isVerbose = (isVerb != null?  isVerb || isSuperVerbose : isVerbose || isSuperVerbose);	// superverbose is an overriding option
	}
	
	/**
	 * Runs the match:
	 * - starts data reader in a thread;
	 * - starts matcher workers in thread pool.
	 * 
	 * Match is done when all threads are finished.
	 * 
	 * @param out an OutpuStream to write result into. 
	 * @throws IOException some Readers managed outside try-with-resources may throw.
	 * @return number of matches found
	 */
	@Override
	public long match(OutputStream out) throws IOException  {
		
	    long matchCount = 0;

	    try {
		    // create data reader
			if (isVerbose) System.out.println("START");
			BufferedDataReader reader = BufferedDataReader.Builder.builder(dataFileLocation, queryFileLocation)
					.withVerbose(isVerbose)
					.withVeryVerbose(isSuperVerbose)
					.withbufferSize(bufferSize)
					.build()
					;
			// prior to using - must init
			int qryRowCount = reader.init();
			if (isVerbose) System.out.println("Read "+ qryRowCount + " queries");
	
			// create workers
			List<MatcherWorker> workers = new ArrayList<MatcherWorker>();
			for (int i = 0; i < poolSize; i++) {
				workers.add(new MatcherWorker(reader, reader, System.out, isVerbose, isSuperVerbose));			
			}
			
			ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
			ExecutorService workerExecutor = Executors.newFixedThreadPool(poolSize);
	
			// launch all:
			// reader
			Future<Long> readerResult = readerExecutor.submit(reader);
		    
			// workers
			List<Future<Long>> matchResults = workerExecutor.invokeAll(workers);
			
			System.out.flush();	
			readerExecutor.shutdown();	
			workerExecutor.shutdown();	
			
		    long processedCount = readerResult.get();
		    if (isVerbose) System.out.println("Records processed: " + processedCount);
	
		    for (Future<Long> fL : matchResults) {
		    	matchCount += fL.get();
		    }
	    }
	    catch (Exception e) {
	    	throw new RuntimeException("Error running match.", e);
	    }
		
		return matchCount;
	}

	public static void main(String[] args) {
		
		ConcurrentRecordMatcher matcher = new ConcurrentRecordMatcher(
//				"/Users/leonidtomilchik/Documents/06.Dev/src-github/large-file-reader/data/records.txt", 
//				"/Users/leonidtomilchik/Documents/06.Dev/src-github/large-file-reader/data/queries.txt", 
				"https://s3.amazonaws.com/idt-code-challenge/records.txt", 
				"https://s3.amazonaws.com/idt-code-challenge/queries.txt", 
				3,
				2700000,
				true,
				false
				);
		long now = System.currentTimeMillis();
		try {
			matcher.match(System.out);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		long elapsed = System.currentTimeMillis() - now;
		System.out.println("elapsed: " + elapsed + " ms");
	}
	
	
}
