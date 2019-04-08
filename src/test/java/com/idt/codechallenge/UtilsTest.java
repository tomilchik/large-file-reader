package com.idt.codechallenge;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Collection of JUnit tests for Utils class.
 * 
 * IMPORTANT: do NOT run via IDE RunAsJUnitTest - this may pull in wrong JUnit classes.
 * Running with mvn test insures only explicitly specified dependencies are used. 
 * 
 * @author leonidtomilchik
 *
 */
@RunWith(PowerMockRunner.class)
public class UtilsTest extends AbstractTest {
	
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

	/**
	 * Tests Utils.isFile() on existing file.
	 * Not catching any exceptions - not the goal here.
	 * Paths must be mocked.
	 */
	@Test
	public void test_isFile_ExistingFile() throws Exception {
		String fileName = "exists-in-memory-" + UUID.randomUUID() + ".zip";
		// 1) create a file in the fake filesystem
        Path tmpPath = Files.createFile(getPath(fileName));
        
        // 2) TEST
        boolean isFile = Utils.isFile(fileName); 
        assertTrue(isFile); 
        
        // 3) cleanup: test-specific stuff
        Files.delete(tmpPath);
 	}
	
	/**
	 * Tests Utils.isFile() on non-existent file.
	 * Not catching test prep exceptions - not the goal here.
	 * Paths must be mocked.
	 */
	@Test
	public void test_isFile_NonExistentFile() throws Exception {
		
		String fileName = "not-exists-in-memory-" + UUID.randomUUID() + ".zip";
        
        // 2) TEST
        boolean isFile = Utils.isFile(fileName);
        assertFalse(isFile); 
        
 	}

	/**
	 * Tests Utils.openReader() on existing file.
	 * Not catching exceptions - do not expect any.
	 * Paths must be mocked.
	 * @throws IOException 
	 */
	@Test
	public void test_openReader_ExistingFile() throws IOException {
		String fileName = "exists-in-memory-" + UUID.randomUUID() + ".zip";

		// 1) create a file in the fake filesystem
        Path tmpFile = Files.createFile(getPath(fileName));
        assertTrue(Files.exists(tmpFile)); // ensure file was created
        		
        // 2) TEST
        try (BufferedReader br = Utils.openReader(fileName, 100)) {
            assertNotNull(br);         	
        }
        catch (Exception e) {
        	// not expected
        	throw new RuntimeException("Failed to open reaader for: " + fileName, e);
        }
        
        // 3) cleanup test-specific 
        Files.delete(tmpFile);
	}
	
	/**
	 * Tests Utils.openReader() on non-existing file.
	 * Exceptions must be asserted.
	 * 
	 * Cannot use @Test(expected IOException.class): 
	 * some prep steps include IOExceptions that are not expected, and must be treated as fail.
	 * 
	 * @throws IOException 
	 */
	@Test
	public void test_OpenReader_NonExistingFile() {
		String fileName = "not-exists-in-memory-" + UUID.randomUUID() + ".zip";
		
        assertFalse(Files.exists(getPath(fileName))); // ensure file does not exist
        
        // TEST. Must use try-with coz test is designed to throw.
        try (BufferedReader br = Utils.openReader(fileName, 100)) {
            fail("opening reader for non-existent file is supposed to throw");         	
        }
        catch (IOException e) {
        	// expected
        }
        catch (Exception e) {
        	// other types not expected
        	throw new RuntimeException("Failed to open reaader for: " + fileName, e);
        }

 	}	
	
	@Test
	public void test_getFileLocationType_NonExisting() throws FileNotFoundException {
		String fileName = "not-exists-in-memory-" + UUID.randomUUID() + ".zip";
        assertFalse(Files.exists(getPath(fileName))); // ensure file does not exist
        
        // TEST
        try {
        	int locationType = Utils.getFileLocationType(fileName);	
        }
        catch (Exception e) {
        	// good!
        	return;
        }
    	fail("Should not be able to read locaiton type of non-existent file");
        
	}
	
	@Test(expected = Test.None.class)
	public void test_getFileLocationType_Existing() throws IOException {
		String fileName = "exists-in-memory-" + UUID.randomUUID() + ".zip";
        assertFalse(Files.exists(getPath(fileName))); // ensure file does not exist
 
		// 1) create a file in the fake filesystem
        Path tmpFile = Files.createFile(getPath(fileName));
        assertTrue(Files.exists(tmpFile)); // ensure file was created
        
        // TEST
        int locationType = Utils.getFileLocationType(fileName);	

        assertTrue (locationType == Utils.LOCATIONTYPE_FILE);

	}
	
	@Test
	public void test_getFileLocationType_NonExistingUrl() throws FileNotFoundException {
		String fileName = "not-exists-in-memory-" + UUID.randomUUID() + ".zip";
		Path pfile = getPath(fileName);
        assertFalse(Files.exists(pfile)); // ensure file does not exist
        
		// construct URL-looking file path
        String url = "file://" + pfile.toAbsolutePath().toString();
        // TEST
        try {
        	int locationType = Utils.getFileLocationType(url);	
        }
        catch (Exception e) {
        	// good!
        	return;
        }
    	fail("Should not be able to read locaiton type of non-existent file");
        
	}
	
	@Test(expected = Test.None.class)
	@Ignore // TODO gotta work out the details around constructing file URL
	public void test_getFileLocationType_ExistingUrl() throws IOException {
		String fileName = "exists-in-memory-" + UUID.randomUUID() + ".zip";
        assertFalse(Files.exists(getPath(fileName))); // ensure file does not exist
 
		// 1) create a file in the fake filesystem
        Path tmpFile = Files.createFile(getPath(fileName));
        assertTrue(Files.exists(tmpFile)); // ensure file was created
        
		// construct URL-looking file path
        String url = "file://" + tmpFile.toAbsolutePath().toString();

        // TEST
        int locationType = Utils.getFileLocationType(url);	

        assertTrue (locationType == Utils.LOCATIONTYPE_URL);

	}

	
	@Test
	public void test_isUrl_Existing() throws Exception {
		// PowerMock refuses to mock java.net.URL - gotta use real connection (TODO: this is bad)
        // TEST
        boolean isUrl = Utils.isUrl("https://www.google.com");
        assertTrue("Valid and available URL should not fail", isUrl);
        
	}

	@Test
	public void test_isUrl_NonExisting() throws Exception {
		// PowerMock refuses to mock java.net.URL - gotta use real connection (TODO: this is bad)
        // TEST
        boolean isUrl = Utils.isUrl("http://" + UUID.randomUUID() + ".com");
        assertFalse("Invalid URL should not work", isUrl);       
	}

	@Test
	public void test_isUrl_Malformed() throws Exception {
		// PowerMock refuses to mock java.net.URL - gotta use real connection (TODO: this is bad)
        // TEST
        boolean isUrl = Utils.isUrl("this does not look like URL");
        assertFalse("Invalid URL should not work", isUrl);       
	}
	
	@Test
	public void test_getEstimatedMemoryRatio() {
		// TODO add meaningful test
	}

}
