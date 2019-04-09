package com.idt.codechallenge.concurrent;

/**
 * This interface returns data - one line at a time.
 * It also knows whether it has more data left.
 * 
 * Checking nextLine() for null is not enough, since this interface 
 * is implemented by a class that:
 * - reads data from the source line at a time;
 * - may have multiple consumers of data.
 * 
 * Fast consumption may temporarily empty out the reader while there may be more data at the source.
 * 
 * @author leonidtomilchik
 *
 */
public interface DataReader {
	
	public String readLine();
	public boolean willHaveMore();

}
