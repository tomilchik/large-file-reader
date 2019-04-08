package com.idt.codechallenge;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Ragtag collection of support methods for the RecordMatcher.
 * 
 * IMPORTANT. Only java.nio for file operations!
 * JUnit tests depend on Google JIMFS in-memory FS to seamlessly substitute for actual FS,
 * and JIMFS does it against java.nio.
 * 
 * Everybody has util classes...
 * 
 * @author leonidtomilchik
 *
 */
class Utils {

	final static int LOCATIONTYPE_FILE = 0;
	final static int LOCATIONTYPE_URL = 1;
	
	// this allows to plug in a custom FS, for testability mostly
	private static FileSystem fs;
	
	/**
	 * Sets a different FS to be used by this class. 
	 * 
	 * IMPORTANT! To get a Path - this class should NOT use Paths.get(name), but its own getPath(...).
	 * This setup supports unit testing, as well as hooking up a different FS when needed.
	 * 
	 * @param otherFS other file system (e.g. JIMFS)
	 */
	static synchronized void init(FileSystem otherFS) {
		fs = otherFS;
	}
	
	/**
	 * Restores FS to whatever the default is.
	 */
	static synchronized void init() throws IOException {
		if (fs != null) {
			fs.close();
			fs = null;
		}
	}

	/**
	 * Figures out whether passed string points to a valid and reachable URL, or to an existing local file
	 * @param fileLocation
	 * @return int representing one of location types: LOCATIONTYPE_FILE, LOCATIONTYPE_URL.
	 * @throws FileNotFoundException when location is neither
	 */
	static int getFileLocationType(String fileLocation) throws FileNotFoundException {
//System.out.println("U.getFileLocationType "+fileLocation);		
		if (isFile(fileLocation)) {
			return LOCATIONTYPE_FILE;
		}
		else if (isUrl(fileLocation)) {
			return LOCATIONTYPE_URL;
		}
		else {
			throw new FileNotFoundException("Invalid file location: '" + fileLocation + "' is neither a file nor a reachable URL.");
		}		
	}
	
	/**
	 * Opens file at specified location (URL, or full file name) as BufferedInputStream
	 * @param fileLocation
	 * @param locationType
	 * @return opened BufferedInputStream
	 * @throws IOException
	 */
	static BufferedReader openReader(String fileLocation, int bufferSize) throws IOException, FileNotFoundException {
		int locationType = getFileLocationType(fileLocation);
		BufferedReader breader = null;
		if (locationType == LOCATIONTYPE_FILE) {
			breader = new BufferedReader(Files.newBufferedReader(getPath(fileLocation)), bufferSize);
		}
		else if (locationType == LOCATIONTYPE_URL) {
			breader = new BufferedReader(new InputStreamReader(new URL(fileLocation).openStream()), bufferSize);			
		}
		// if neither type matches - return whatever
		return breader;
	}

	/**
	 * Checks whether fileLocaiton is a valid and reachable URL.
	 * Exposed as static method to enable user-friendly command-line invocations.
	 * @param fileLocation
	 * @return true if it is, false if it isn't
	 */
	static boolean isUrl(String fileLocation) {
		// best way to check - try to connect; any error - not a valid/reachable URL
//System.out.println("U.isUrl "+fileLocation);		
		try {
		    URLConnection conn = new URL(fileLocation).openConnection();
		    conn.connect();
		    if (conn.getContentType() == null) return false;
		}
		catch (Exception e) {
			return false;
		}
		return true;
	}
	

	/**
	 * Checks whether fileLocation points to an existing file.
	 * Exposed as static method to enable user-friendly command-line invocations.
	 * @param fileLocation
	 * @return true if it is, false if it isn't
	 */
	static boolean isFile(String fileLocation) {
		return Files.exists(getPath(fileLocation));
	}
	
	/**
	 * Silently execute the memory estimate
	 * @return
	 */
	static double getEstimatedMemoryRatio() {
		return getEstimatedMemoryRatio(false);
	}
	
	/**
	 * If alternative FS is set - gets Path from it.
	 * Otherwise - from default FS.
	 */
	static private Path getPath(String first, String... more) {
		if (fs != null) return fs.getPath(first, more);
		return Paths.get(first, more);
	}
	
	/**
	 * Returns an estimate of free-to-total memory ratio for the current process.
	 * @return estimated free memory in bytes
	 */
	static double getEstimatedMemoryRatio(boolean isVerbose) {
		Runtime rt = Runtime.getRuntime();
		long maxMem = rt.maxMemory();		// max available to JVM
		long totalMem = rt.totalMemory();	// total allocated to JVM currently
		long freeMem = rt.freeMemory(); 	// total free (part of the total allocated)
		
		long estimatedFreeMem = maxMem - (totalMem - freeMem);
		double freeToMaxRatio = (double)estimatedFreeMem/maxMem;		
		
		NumberFormat nf = NumberFormat.getInstance();
		DecimalFormat df = new DecimalFormat("#0.00");
		if (isVerbose) {
			System.out.println("Max memory:                 " + nf.format(maxMem));
			System.out.println("Total allocated memory:     " + nf.format(totalMem));
			System.out.println("Free memory:                " + nf.format(freeMem));
			System.out.println("Free/Max ratio:             " + df.format(freeToMaxRatio));
			System.out.println("Estimated full free memory: " + nf.format(estimatedFreeMem));
		}
		
		return freeToMaxRatio;
	}
}
