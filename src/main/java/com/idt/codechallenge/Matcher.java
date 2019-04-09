package com.idt.codechallenge;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Interface for different matcher impls.
 * @author leonidtomilchik
 *
 */
public interface Matcher {
	public long match(OutputStream out) throws IOException;
}
