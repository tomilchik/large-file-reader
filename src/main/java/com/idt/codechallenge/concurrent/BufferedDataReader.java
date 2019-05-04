package com.idt.codechallenge.concurrent;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import com.idt.codechallenge.Utils;

/**
 * Data reader that will simultaneously:
 * - read data line by line from the source;
 * - give consumers data one line at a time.
 * 
 * This in theory may improve performance of the matching process.
 * 
 * @author leonidtomilchik
 *
 */
class BufferedDataReader implements DataReader, QueryHolder, Callable<Long> {

	// some defaults for RecordMatcher initializer
//	private final static double DEFAULT_MEMORY_RATIO = 0.5d;
	private final static boolean DEFAULT_VERBOSE = false;
	private final static boolean DEFAULT_SUPERVERBOSE = false;
	private final static int DEFAULT_BUFFERSIZE = 8192;

	private final String dataFileLocation;
	private final String queryFileLocation;
	
	private boolean isVerbose = DEFAULT_VERBOSE;
	private boolean isSuperVerbose = DEFAULT_SUPERVERBOSE;
//	private double minFreeMemoryRatio = DEFAULT_MEMORY_RATIO;
	private int bufferSize = DEFAULT_BUFFERSIZE;

	private List<Set<String>> queries = null;
	private ConcurrentLinkedQueue<String> data;
	
	// if true - source still has more; when false - no more data in the source
	private boolean willHaveMore = true;
	
	public BufferedDataReader(
			String dflLocation, 
			String qfLocation, 
//			Double  minMemRatio, 
			Integer bufSize, 
			Boolean isVerb, 
			Boolean isSuperVerb) {

		if (dflLocation == null) throw new IllegalArgumentException("dataFileLocation cannot be null");
		this.dataFileLocation = dflLocation;

		if (qfLocation == null) throw new IllegalArgumentException("queryFileLocation cannot be null");
		this.queryFileLocation = qfLocation;
		
		this.isSuperVerbose = (isSuperVerb != null? isSuperVerb : isSuperVerbose);;
		this.isVerbose = (isVerb != null?  isVerb || isSuperVerbose : isVerbose || isSuperVerbose);	// superverbose is an overriding option
		
//		if (minMemRatio != null && (minMemRatio <= 0 || minMemRatio > 1)) throw new IllegalArgumentException("minFreeMemoryRatio must be a positive floating point number <= 1.0");
//		this.minFreeMemoryRatio = (minMemRatio != null? minMemRatio : DEFAULT_MEMORY_RATIO);
		
		if(bufSize !=  null && bufSize < 0 ) throw new IllegalArgumentException("bufferSize must be positive. Java default is " + DEFAULT_BUFFERSIZE);
		this.bufferSize = (bufSize != null? bufSize : DEFAULT_BUFFERSIZE);
	}		

	@Override
	public List<Set<String>> getQueries() {
		return queries;
	}

	@Override
	public String readLine() {
		return data.poll();
	}

	@Override
	public boolean willHaveMore() {
		return willHaveMore;
	}
	
	/**
	 * Callable impl:
	 * starts reading from the source, filling up the internal data storage.
	 */
	@Override
	public Long call() throws Exception {
		if (queries == null) throw new IllegalStateException("Reader.init() must be called first before starting it.");

		info("Reader STARTED...");

		willHaveMore = true;

		long rowCount = 0;
		data = new ConcurrentLinkedQueue<String>();

		// reader for data file
		BufferedReader dreader = Utils.openReader(dataFileLocation, bufferSize);
		
		String line;
		long now = System.currentTimeMillis();
		
		while ((line = dreader.readLine()) != null) {
		
			debug("line: " + line);
			// add to internal queue
			data.add(line);
			rowCount++;
			
			// TODO check memory, stop temporarily 
		}
		
		long elapsed = System.currentTimeMillis() - now;
		info("Reader finished in " + elapsed +" ms");

		willHaveMore = false;

		return rowCount;
	}
	
	/**
	 * Initializes this reader: reads in the entire list of queries
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	int init() throws FileNotFoundException, IOException {
		queries = new ArrayList<Set<String>>();

		// reader for queries		
		BufferedReader qreader = Utils.openReader(queryFileLocation, bufferSize);

		List<Set<String>> tempQueries = new ArrayList<Set<String>>();
		
		String line;
		while ((line = qreader.readLine()) != null) {
			// convert and add to internal list
			tempQueries.add(Arrays.asList(line.split(",")).stream().collect(Collectors.toSet()));
		}
		// TODO check if all queries have unique word sets?

		// reload queries into immutable list
		queries = Collections.unmodifiableList(tempQueries);
		return queries.size();
	}


	/**
	 * Builder. The BufferedDataReader ctor has too many args - awkward to create new.
	 * @author leonidtomilchik
	 *
	 */
	static class Builder {
		private final String dataFileLocation;
		private final String queryFileLocation;
		
		private boolean isVerbose = DEFAULT_VERBOSE;
		private boolean isSuperVerbose = DEFAULT_SUPERVERBOSE;
//		private double minFreeMemoryRatio = DEFAULT_MEMORY_RATIO;
		private int bufferSize = DEFAULT_BUFFERSIZE;
		
		/**
		 * Data and query files locations are mandatory; the rest - optional.
		 * @param dfleLocation
		 * @param qfLocation
		 * @return
		 */
		static Builder builder(String dfleLocation, String qfLocation) {			
			return new Builder(dfleLocation, qfLocation);
		}
		
		private Builder(String dfleLocation, String qfLocation) {
			this.dataFileLocation = dfleLocation;
			this.queryFileLocation = qfLocation;		
		}
		
		Builder withVerbose(boolean v) {this.isVerbose = v; return this;}
		Builder withVeryVerbose(boolean v) {this.isSuperVerbose = v; return this;}
//		Builder withMinFreeMemoryRatio(double d) {this.minFreeMemoryRatio = d; return this;}
		Builder withbufferSize(int bs) {this.bufferSize = bs; return this;}
		
		BufferedDataReader build() {
			return new BufferedDataReader(dataFileLocation, queryFileLocation, /*minFreeMemoryRatio,*/ bufferSize, isVerbose, isSuperVerbose);
		}
	}

	
	private void info(Object message) {
		if (isVerbose) System.out.println("[" + Thread.currentThread().getName()+"] "+message);
	}
	
	private void debug(Object message) {
		if (isSuperVerbose) System.out.println("[" + Thread.currentThread().getName()+"] "+message);
	}

}
