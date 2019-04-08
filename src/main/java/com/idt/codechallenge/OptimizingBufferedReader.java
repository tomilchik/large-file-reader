package com.idt.codechallenge;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class is a memory-optimized line reader from a file, with optional conversion of each line into an object of caller's choice
 * (conversion output is defined by a plugged-in lambda).
 * 
 * This class optimizes access to data in a file at specified location.
 * The location can be local file path or URL.
 * 
 * The file is assumed to be readable as lines.
 * Optional: each line may be converted according to a Java streams lambda (Function; takes one value in, returns another value) passed into ctor.
 * 
 * Optimization: trying to load the whole file into memory, and read from memory during subsequent line reads.
 * This class is backed by a BufferedReader.
 * 
 * If this class detects that during initial load the memory consumption grows too close to the specified limit -
 * it will abandon the load, and will only use internal BufferedReader going forward.
 * 
 * Thread safety: not safe, since it maintains internal state of the reader.
 * 
 * TODO If we drop dependency on Utils (this package) - this could be a quite useful little utility...
 * 
 * @author leonidtomilchik
 *
 */
class OptimizingBufferedReader <R> implements Closeable {
	
	private final static boolean DEFAULT_ISOPTIMIZE = false;

	/**
	 * Either current index in the internal list, or count of lines retrieved by internal reader
	 */
	private int currentIdx = 0;
	
	/**
	 * This is where fully pre-buffered contents would be stored
	 */
	private List<R> lines = null;

	/**
	 * reader; used 1)to load the lines; 2)to read the input line by line when invoked iteratively
	 */
	private BufferedReader internalReader = null;

	/**
	 * Converter to apply to each line read from the source
	 */
	private final Function<String, R> stringToSetConverter;
	
	/**
	 * Limit of ratio free_memory / total_available_memory; going lower than this means we may be close to out-of-memory
	 */
	private double minFreeMemoryRatio;
	
	/**
	 * Location of the input file. Can be URL, or full local file path.
	 */
	private String fileLocation;
	
	/**
	 * Size of internal reader's buffer.
	 */
	private int bufferSize;	
	
	private boolean isOptimize = DEFAULT_ISOPTIMIZE;
	
	/**
	 * Full arg set ctor.
	 * @param fLocation file location. URL or full local path.
	 * @param bSize size of internal reader's buffer to allocate
	 * @param converterFunction a lambda (Function) to convert lines from the file into whatever.
	 * @throws IOException
	 */
	OptimizingBufferedReader(String fLocation, boolean isOpt, double minMemRatio, int bSize, Function<String,R> converterFunction) throws IOException {
		fileLocation = fLocation;
		isOptimize = isOpt;
		bufferSize = bSize;
		minFreeMemoryRatio = minMemRatio;
		stringToSetConverter = converterFunction;
		
		if (!isOptimize) {
			// if not optimizing - create internal reader right away, no pre-reads will be executed
			resetReader();
		}
	}

	/**
	 * With default converter (no conversion, returns lines as-is)
	 * @param fLocation
	 * @param bSize
	 * @throws IOException
	 */
	OptimizingBufferedReader(String fLocation, double minMemRatio, int bSize) throws IOException {
		this(fLocation, DEFAULT_ISOPTIMIZE, minMemRatio, bSize, (s) -> {return (R)s;}); // TODO investigate: compiler won't resolve without casting - ???
	}

	/**
	 * With default buffer size.
	 * @param fLocation
	 * @param converterFunction
	 * @throws IOException
	 */
	OptimizingBufferedReader(String fLocation, double minMemRatio, Function<String,R> converterFunction) throws IOException {
		this(fLocation, DEFAULT_ISOPTIMIZE, minMemRatio, 8192, converterFunction);
	}

	/**
	 * Reads a line from whatever current line source is: lines or internal reader
	 * @return
	 * @throws IOException
	 */
	R readLine() throws IOException {
		
		// always check first if lines have been populated already
		if (lines != null) {
//System.out.println(fileLocation+": reading lines...");			
			if (lines.isEmpty() || currentIdx >= lines.size()) {
				// reached end of contents; reset the line index (next call will return line[0])
				resetLines();
				return null;
			}
			// no conversion needed - lines already have contents pre-converted upon initial load
			return lines.get(currentIdx++);
		}
		// next: see if internal reader is already open
		else if (internalReader != null) {
//System.out.println(fileLocation+": reading buffer...");			
			String line = internalReader.readLine();
			if (line != null) {
				// convert and return
				currentIdx++;
				return stringToSetConverter.apply(line);
			}
			// buffer has no more lines, reset 
			resetReader();
			return null;
		}
		// no lines, no reader - init, then try again, result will be different
		init();
		return readLine();
	}

	/**
	 * Resets lines collection only
	 */
	private void resetLines() {
		currentIdx = 0;
	}
	
	/**
	 * Resets internal reader only: nulls out
	 * @throws IOException
	 */
	private void resetReader() throws IOException {
		if (internalReader != null) {
			internalReader.close();
			internalReader = null;
		}
		internalReader = Utils.openReader(fileLocation, bufferSize);
	}
	
	/**
	 * initializer.
	 * Called internally, only once - when no reads have occurred yet.
	 * Will try to read full file into list;
	 * will give up if memory limit is reached, and will switch to BufferedReader reads going forward.
	 * 
	 * If memory is in safe zone - will be reading from the internal list going forward.
	 */
	private void init() throws IOException {
		// create and open the internal reader if it does not exist, 
		// or re-create and open a new one if it already existed
		resetReader();	
		if (!isOptimize) return;	// we are in non-optimizing mode - no pre-reads at all
		
		lines = new ArrayList<R>();
		
		boolean isLimitReached = false;
		String line = null;
		// try to read the whole source in at once, converting each line;
		// give up when memory allocation overshoots the set limit
		while ((line = this.internalReader.readLine()) != null) {
			// convert and add to internal list
			lines.add(stringToSetConverter.apply(line));
			// after every read - check memory
			if (Utils.getEstimatedMemoryRatio() < minFreeMemoryRatio) {
				// below the limit; stop reading, switch over to inefficient but safe buffered reads of queries from the file for each data row
				isLimitReached = true;
				break; // outta loop - now!
			}
		}
		
		// limit reached - kill the lines, re-create the internal reader:
		// going forward this reader will read one line at a time from the remote source
		if (isLimitReached) {
			lines = null;
			resetReader();
		}
	}
	
	/**
	 * Closeable impl: closes the internal reader if it exists.
	 * This class can be used in try-with-resources.
	 */
	public void close() throws IOException {
		if (internalReader != null) {
			internalReader.close();
			internalReader = null;
		}	
		lines = null;
		currentIdx = 0;
	}
	
	/**
	 * A string representation of this instance.
	 */
	public String toString() {
		return "OptimizingBufferedReader: line=" + (currentIdx + 1) + "; source="+ (lines != null? lines : fileLocation);
	}

	/**
	 * This is just a test harness
	 * @param args
	 */
	public static void main(String[] args ) {
		try (
			OptimizingBufferedReader<String> obr = 
					new OptimizingBufferedReader<String>(
							"https://s3.amazonaws.com/idt-code-challenge/queries.txt",
							0.5d,
							8092
//							,(s) -> {return Arrays.asList(s.split(",")).stream().collect(Collectors.toSet());}
					);
				) {
			String line;
			while ((line = obr.readLine()) != null) {
				System.out.println(line);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}

