package com.idt.codechallenge;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.*;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;


/**
 * Collection of JUnit tests for RecordMatcher.
 * 
 * @author leonidtomilchik
 *
 */
@RunWith(PowerMockRunner.class)
//@Ignore
public class RecordMatcherTest extends AbstractTest {
	
	private final static String DEFAULT_DATAFILE = "";
	private final static String DEFAULT_QUERYFILE = "";

	private static FileSystem fs;
	
	/**
	 * Test setup - once for all tests.
	 */
	@BeforeClass
	public static void setupAll() {
		AbstractTest.setupAll();
	}

	@AfterClass
	public static void teardownAll() throws IOException {
		AbstractTest.teardownAll();
	}

	/**
	 * Test setup - once for each test.
	 * 
	 */
	@Before
	public void setupOne() {
		// nothing to do so far
	}

	@After
	public void teardownOne() {
		// nothing to do so far
	}
	
	@Test 
	public void test_newRecordMatcher() throws Exception {		
		RecordMatcher rm = new RecordMatcher("", "");		
		assertNotNull(rm);
	}

	@Test 
	public void test_newRecordMatcher_ValidArguments() throws Exception {		
		RecordMatcher rm = new RecordMatcher("", "", 0.1d, 100, false, true, false, false);	
		assertNotNull(rm);
	}

	@Test 
	public void test_newRecordMatcher_DefaultArguments() throws Exception {		
		RecordMatcher rm = new RecordMatcher("", "", null, null, null, null, null, null);	
		assertNotNull(rm);
	}

	@Test (expected = IllegalArgumentException.class)
	public void test_newRecordMatcher_InvalidDataFile() throws Exception {		
		RecordMatcher rm = new RecordMatcher(null, "");
		assertNull(rm);
	}

	@Test (expected = IllegalArgumentException.class)
	public void test_newRecordMatcher_InvalidQueryFile() throws Exception {
		RecordMatcher rm = new RecordMatcher("", null);		
		assertNull(rm);
	}

	@Test (expected = IllegalArgumentException.class)
	public void test_newRecordMatcher_NegativeMemoryRatio() throws Exception {
		RecordMatcher rm = new RecordMatcher("", "", -1d, 1, false, true, false, false);		
		assertNull(rm);
	}

	@Test (expected = IllegalArgumentException.class)
	public void test_newRecordMatcher_TooLargeMemoryRatio() throws Exception {
		RecordMatcher rm = new RecordMatcher("", "", 1.1d, 1, false, true, false, false);		
		assertNull(rm);
	}

	@Test (expected = IllegalArgumentException.class)
	public void test_newRecordMatcher_NegativeBufferSize() throws Exception {
		RecordMatcher rm = new RecordMatcher("", "", 0.5d, -1, false, true, false, false);		
		assertNull(rm);
	}

	/**
	 * Runs match with random inputs. Expected: matcher does not blow up, and match count: 0 
	 * @throws Exception
	 */
	@Test
	public void test_match_Random() throws Exception {
		
		// random words, match not guaranteed
		String dataFile = populateFile("data-"+UUID.randomUUID() + ".txt", 100, 10, 5, ",");	// bigger
		String queryFile = populateFile("queries-"+UUID.randomUUID() + ".txt", 10, 5, 5, ",");	// smaller

		Path pdf = getPath(dataFile);
		Path qdf = getPath(queryFile);
		
		assertTrue(Files.exists(pdf));
		assertTrue(Files.exists(qdf));

		RecordMatcher rm = new RecordMatcher(dataFile, queryFile);		

		// TEST
		long rowcount = rm.match(System.out);		
		System.out.println("matched.rows="+rowcount);
		
		assertTrue(rowcount == 0);

		// cleanup
		Files.delete(pdf);
		Files.delete(qdf);
		
		assertFalse(Files.exists(pdf));
		assertFalse(Files.exists(qdf));
	}

	/**
	 * Runs match with random inputs, test the -v flag.
	 * @throws Exception
	 */
	@Test
	public void test_match_withVerboseOn() throws Exception {
		
		// random words, match not guaranteed
		String dataFile = populateFile("data-"+UUID.randomUUID() + ".txt", 100, 10, 5, ",");	// bigger
		String queryFile = populateFile("queries-"+UUID.randomUUID() + ".txt", 10, 5, 5, ",");	// smaller

		Path pdf = getPath(dataFile);
		Path qdf = getPath(queryFile);
		
		assertTrue(Files.exists(pdf));
		assertTrue(Files.exists(qdf));

		RecordMatcher rm = new RecordMatcher(dataFile, queryFile, null, null, null, null, true, null);		

		// TEST
		long rowcount = rm.match(System.out);		
		System.out.println("matched.rows="+rowcount);
		
		// TODO gotta catch verbose statements into System.out
		assertTrue(rowcount == 0);

		// cleanup
		Files.delete(pdf);
		Files.delete(qdf);
		
		assertFalse(Files.exists(pdf));
		assertFalse(Files.exists(qdf));
	}

	/**
	 * Runs match with predictable outcome
	 * @throws Exception
	 */
	@Test
	public void test_match_WithMatches() throws Exception {
		
		String dataExpect5matches = 
				"cat,dog,lizard,lizard,lion\r\n"					// match; expect: {lion:1,lizard:2}
				+"du,du,du,da,da,da\r\n"							// not a match
				+"cat,is,an,alien\r\n"								// not a match
				+"mon,tue,wed,thu,fri,sat,mon\r\n"					// match; expect: {mon:2,fri:1,sat:1} 
				+"ask,me,about,cat,and,dog,on,wed,and,thu\r\n"		// match; expect: 
																	// {ask:1,me:1,about:1,and:2,on:1,wed:1,thu:1}, 
																	// {about:1,cat:1,and:2,dog:1,on:1,wed:1,thu:1}
																	// {ask:1,me:1,about:1,cat:1,and:2,dog:1,on:1}				
				;
		String qry = 
				"cat,dog\r\n"
				+"ask,me\r\n"
				+"wed,thu\r\n"
				;
				
		System.out.println("data file:");
		System.out.println(dataExpect5matches);
		System.out.println("query file:");
		System.out.println(qry);

		String dataFile = "data-" + UUID.randomUUID() + ".txt";
		String queryFile = "queries-"+ UUID.randomUUID() + ".txt";

		Path pdf = getPath(dataFile);
		Path pqf = getPath(queryFile);
		
		Files.write(pdf, dataExpect5matches.getBytes());
		Files.write(pqf, qry.getBytes());
		
		long expectedMatchCount = 5;

		// TEST
		RecordMatcher rm = new RecordMatcher(dataFile, queryFile);		
		
		long actualMatchCount = rm.match(System.out);
		
		assertEquals("Wrong # of matches found", expectedMatchCount, actualMatchCount);
		
		// cleanup
		Files.delete(pdf);
		Files.delete(pqf);
		
		assertFalse(Files.exists(pdf));
		assertFalse(Files.exists(pqf));
	}

	/**
	 * Makes a fake query file.
	 * Format: CSV
	 * @param string
	 * @return
	 * @throws IOException 
	 */
	private String populateFile(String fname, int lineCount, int wordLen, int wordCount, String delim) throws IOException {
		
		Path fpath = getPath(fname);
		StringBuffer sb = new StringBuffer(lineCount*(wordLen + 1));
		for (int i = 0; i < lineCount; i++) {
			sb.append(randomString(wordLen, wordCount, delim) + "\r\n");
		}
		Files.write(fpath, sb.toString().getBytes());
		System.out.println("file fname: " + fname);
		System.out.println(sb.toString());
		return fpath.toAbsolutePath().toString();
	}
	
	/**
	 * Generates random string of specified length
	 * @param len
	 * @return
	 */
	private String randomString(int wordLen, int wordCount, String delim) {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < wordCount; i++) {
			sb.append(RandomStringUtils.randomAlphabetic(wordLen));
			if (i + 1 < wordCount) sb.append(delim);
		}
	    return sb.toString();	 
	}

}
