package com.idt.codechallenge;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.idt.codechallenge.concurrent.ConcurrentRecordMatcher;

/**
 * This class runs RecordMatcher: a matcher that implements an algorithm for matching data records against set of queries.
 * For full description of the functionality see method {@link #help(Options) help}.
 * 
 * Files to use to test:
 * - data: https://s3.amazonaws.com/idt-code-challenge/records.txt;
 * - queries: https://s3.amazonaws.com/idt-code-challenge/queries.txt.
 * 	
 * @author leonidtomilchik
 *
 */
public class MatcherRunner {

	// command line option names
	private final static String OPT_CONCURRENT 			= "c";
	private final static String OPT_WORKERCOUNT			= "w";
	private final static String OPT_SUPERVERBOSE 		= "vv";
	private final static String OPT_VERBOSE 			= "v";
	private final static String OPT_OPTIMIZE_DATAREADS 	= "od";
	private final static String OPT_OPTIMIZE_QUERYREADS = "oq";
	private final static String OPT_BUFFERSIZE 			= "b";
	private final static String OPT_HELP 				= "help";
	private final static String OPT_MINFREEMEMRATIO 	= "m";
	
	/**
	 * CLI entry point.
	 * For details regarding command line arguments see method help.
	 * @param args command line args
	 */
	public static void main(String[] args) {
		
		// read command line options
		Options options = getOptions(args);			
		
		try {
			// parse command line
			CommandLineParser parser = new DefaultParser();
			CommandLine line = parser.parse( options, args );

			line = parser.parse( options, args );
			// only asking for help here...
			if (line.hasOption(OPT_HELP)) {
				help(options);
				return;
			}		

			// with all options that are on/off: in absence of a flag let the matcher decide on a default
			Boolean isConcurrent = (line.hasOption(OPT_CONCURRENT)? true : false);					System.out.println("isConcurrent="+isConcurrent);	
			Integer workerCount = null;
			Long val = (Long)line.getParsedOptionValue(OPT_WORKERCOUNT);
			if (val != null) workerCount = val.intValue();											System.out.println("workerCount="+workerCount);		
			Boolean isSuperVerbose = (line.hasOption(OPT_SUPERVERBOSE)? true : null);				System.out.println("isSuperVerbose="+isSuperVerbose);	
			Boolean isVerbose = (line.hasOption(OPT_VERBOSE)? true : null);							System.out.println("isVerbose="+isVerbose);	
			Boolean isOptimizeDataReads= (line.hasOption(OPT_OPTIMIZE_DATAREADS)? true : null);		System.out.println("isOptimizeDataReads="+isOptimizeDataReads);	
			Boolean isOptimizeQueryReads = (line.hasOption(OPT_OPTIMIZE_QUERYREADS)? true : null);	System.out.println("isOptimizeQueryReads="+isOptimizeQueryReads);	
			
			Double minFreeMemRatio = (Double)line.getParsedOptionValue(OPT_MINFREEMEMRATIO);		System.out.println("minFreeMemRatio="+minFreeMemRatio);	
			Integer bufferSize = null;
			val = (Long)line.getParsedOptionValue(OPT_BUFFERSIZE);
			if (val != null) bufferSize = val.intValue();		System.out.println("bufferSize="+bufferSize);	
			
			// and remaining args are positional - file names are not prefixed
			String dataFile = line.getArgList().get(0);												System.out.println("dataFile="+dataFile);	
			String queryFile = line.getArgList().get(1);											System.out.println("queryFile="+queryFile);	
			
			Matcher matcher = null;
			if (isConcurrent) {
				// create concurrent matcher. If parameters are set right - runs ~30% faster.
				matcher = new ConcurrentRecordMatcher(
						dataFile, 
						queryFile,
						workerCount,
						bufferSize,
						isVerbose,
						isSuperVerbose
						);
			}
			else {
				matcher = new RecordMatcher(
						dataFile, 
						queryFile,
						minFreeMemRatio,
						bufferSize,
						isOptimizeDataReads,
						isOptimizeQueryReads,
						isVerbose,
						isSuperVerbose
						);
			}
			
			boolean isVerb = (isVerbose != null? isVerbose : false);
			if (isVerb) {
				System.out.println("data file : "+dataFile);
				System.out.println("query file: "+queryFile);
				System.out.println("Starting match...");
			}
			
			long now = System.currentTimeMillis();
			
			long matchCount = matcher.match(System.out);
			
			long elapsed = System.currentTimeMillis() - now;	
			if (isVerb) {
				System.out.println("DONE! Matches #: " + matchCount + ". Elapsed: "+elapsed+" ms");
			}

		}
		catch(Exception e) {
			e.printStackTrace();
			System.out.println();
			System.out.println(e.getMessage());
			System.out.println();
			help(options);
			return;
		}		
	}

	/**
	 * Creates Options object from the passed command line args.
	 * @param args
	 * @return
	 * @throws IllegalArgumentException
	 */
	private static Options getOptions(String[] args) throws IllegalArgumentException {
		Options options = new Options();

		// add t option
		options.addOption(OPT_CONCURRENT, false, "Run in concurrent mode: data reads and query matches run simutlaneously. May improve perfromrance somewhat."
				+" In this mode the only other options that will have an effect are: "
				+"-" + OPT_BUFFERSIZE + ", -" + OPT_WORKERCOUNT + ", -" + OPT_VERBOSE + " and -" +OPT_SUPERVERBOSE+"; the rest will be ignored." );
		options.addOption(OPT_VERBOSE, false, "Verbose. In addition to the results will print some data such as record counts, JVM memory, etc.");
		options.addOption(OPT_SUPERVERBOSE, false, "Very verbose. Super talkative, spits out data for each data row. Lots of screen output. PERFORMANCE KILLER!");
		options.addOption(OPT_OPTIMIZE_DATAREADS, false, "Tries to optimize data reads by pre-loading all of data into memory. "
				+"Has impact on performance (depends on many factors, such as JVM memory, buffer size, data file location, network speed, etc.) "
				+" If you know that your data file is small enough to fit into memory - USE THIS FLAG.");
		options.addOption(OPT_OPTIMIZE_QUERYREADS, false, "Tries to optimize query reads by pre-loading all of queries into memory. "
				+ "Has impact on performance (depends on many factors, such as JVM memory, buffer size, data file location, network speed, etc.). "
				+" If you know that your query file is small enough to fit into memory - USE THIS FLAG.");
        options.addOption(OPT_HELP, false, "Prints this information.");

		Option opt1 = Option.builder(OPT_WORKERCOUNT)
                .hasArg()
                .argName("WORKERS")
                .desc("(default: 1) When in concurrent mode: number of matcher threads to launch. "
                		+"Experiment with it to see its effect on performance. Typical optimal value is 3-5.")
                .build();
		opt1.setType(Number.class);
		options.addOption(opt1);

		Option opt2 = Option.builder(OPT_MINFREEMEMRATIO)
                .hasArg()
                .argName("MIN_RATIO")
                .desc("(default: 0.5) Base-1, positive number representing the minimal desired ratio of free-to-total-available memory. "
                		+"Setting it low may improve performance against very large files. "
                		+"Setting it too low moves you closer to out-of-memory. "
                		+"Setting it too high will dramatically worsen performance even with relatively small query files. "
                		+"Experiment with it to see its effect on performance.")
                .build();
		opt2.setType(Number.class);
		options.addOption(opt2);
		
		Option opt3 = Option.builder(OPT_BUFFERSIZE)
                .hasArg()
                .argName("SIZE")
                .desc("(default: 8092) positive integer - buffer size for the file readers. Experiment with it to see its effect on performance.")
                .build();
		opt3.setType(Number.class);
		options.addOption(opt3);

		return options;
	}

	/**
	 * Prints help info to console.
	 */
	private static void help(Options options) {
		// automatically generate the help statement from CLI Options (including usage and descriptions of all args)...
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "java [javaopts] MatcherRunner [options] dfile qfile \nusage from jar: java [javaopts] -jar jarfile [options]", options );
		
		// ...and add the long description of what this program does
		System.out.println(
				"dfile    	Data file: full path to file, or a valid URL. File format: CSV. Lines separated by line breaks, comma-separated values within lines.\n" + 
				"qfile    	Query file: full path to file, or a valid URL. File format: CSV. Lines separated by line breaks, comma-separated values within lines.\n" +  
				"javaopts 	standard JVM options\n" +
				"jarfile  	name of the JAR containing MatcherRunner as the entry point.\n" +
				"\n" +
				"This utility processes a large data file consisting of text records where each record represents a list of words. \n" + 
				"Given a set of words (called a query; contained in a query file) a record is considered a match if it contains all of the query words. \n" + 
				"For each query in the query file this utility finds the matching records in the data file and for each matching record outputs the number of times that each non-query word appears. \n" + 
				"For each query and matching record the output is a JSON dictionary with the non-query words and their count.\n" + 
				"\n" + 
				"For example for the following data records:\n" + 
				"red,sky,coin,bucket,chair,blue\n" + 
				"apple,chair,purple,red,house \n" + 
				"silver,blue,apple,coin,street\n" + 
				"\n" + 
				"And the query:\n" + 
				"red,apple\n" + 
				"\n" + 
				"The output will be:\n" + 
				"{chair : 1, purple : 1, house : 1}\n" + 
				"\n" + 
				"The second line (record) matches the query (it contains both `apple` and `red`) so if we count all other non query words this will give us the above output. \n" + 
				"The results are printed to the standard output.\n"
				); 
	}
}
