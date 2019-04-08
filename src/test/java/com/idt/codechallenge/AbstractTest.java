package com.idt.codechallenge;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

/**
 * Parent for unit test classes.
 * 
 * Substitutes JIMFS for actual FS.
 * 
 * BUT!
 * For this to work smoothly (java.nio.Files talks to FS through java.nio.Paths):
 * 
 * instead of calls to Paths.get(name) use fs.getPath(name).
 * 
 * IMPORTANT: do NOT run via IDE RunAsJUnitTest - this may pull in wrong JUnit classes.
 * Running with mvn test insures only explicitly specified dependencies are used. 
 * 
 * @author leonidtomilchik
 *
 */
public class AbstractTest {

	private static FileSystem fs;

	protected static void setupAll() {
		// replace default FS with Google's JIMFS - in-memory FS, so that all I/O happens in memory:
		// cannot count on having access to actual FS folder structure
		fs = Jimfs.newFileSystem(Configuration.unix());
		Utils.init(fs);	
	}

	protected static void teardownAll() throws IOException {
		// return FS to whatever it was before
		Utils.init();
	}

	/**
	 * Replacement for Paths.get(..) - uses substitute FS.
	 * @param fileLocation
	 * @return
	 */
	protected Path getPath(String first, String... more) {
		return fs.getPath(first, more);
	}
	

}
