/*[INCLUDE-IF Sidecar18-SE]*/
/*
 * Copyright IBM Corp. and others 2000
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] https://openjdk.org/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 OR GPL-2.0-only WITH OpenJDK-assembly-exception-1.0
 */
package com.ibm.jvm.format;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

/**
 * Main routine for formatting the trace file. Reads raw trace data and formats
 * in a platform independent manner
 *
 * @author Tim Preece
 */
final public class TraceFormat
{
	protected static final int traceFormatMajorVersion = 1;
	protected static final int traceFormatMinorVersion = 0;

	private TraceArgs traceArgs = null;

	private Vector traceFiles = new Vector();

	private TraceFile traceFile = null;

	private BufferedWriter out;

	private static int generations;

	// Global data
	protected static long lostRecordCount = 0;

	protected static MessageFile messageFile;

	protected static Vector threads;

	protected static int invalidBuffers;

	protected static float verMod = -1.0f;

	protected static BigInteger overallStartSystem;

	protected static BigInteger overallStartPlatform;

	protected static BigInteger first;

	protected static BigInteger last;

	protected static BigInteger lastWritePlatform;

	protected static BigInteger lastWriteSystem;

	protected static BigInteger timeConversion;

	protected static String headings;

	protected static PrintStream outStream;

	protected static PrintStream errStream;

	protected static int expectedRecords;

	private boolean traceFileIsTruncatedOrCorrupt = false;

	private boolean primed = false;

	// constants
	protected static final String usageMessage = "Usage:\n"
/*[IF Sidecar18-SE-OpenJ9]*/
			+ "traceformat input_filespec [output_filespec] \n"
/*[ELSE]*/
			+ "java com.ibm.jvm.format.TraceFormat input_filespec [output_filespec] \n"
/*[ENDIF] Sidecar18-SE-OpenJ9 */
			+ "\t[-summary] [-datdir datfiledirectory] [-uservmid vmid] [-thread:id] [-indent] \n"
			+ "\t[-overridetimezone noOfHours] [-help]\n"
			+ "\n"
			+ "where:\n"
			+ "\tinput_filespec = trace file generated by the jvm to be processed\n"
			+ "\toutput_filespec = name of the formatted file - default is\n"
			+ "\t      input_filespec.fmt\n"
			+ "\tsummary = print summary information to screen without generating\n"
			+ "\t      formatted file\n"
			+ "\n"
			+ "\tdatdir = used when the formatter is used to format a pre 5.0 vm's\n"
			+ "\t      tracefile. The datfilelocation tells the formatter where to\n"
			+ "\t      find the .dat files of the older vm's dat files. Default is\n"
			+ "\t      current directory, and the .dat files can be safely copied\n"
			+ "\t      into the current directory (as long as they don't overwrite\n"
			+ "\t      the current vm's .dat files).\n"
			+ "\tuservmid = users can specify a string to be inserted into each\n"
			+ "\t      tracepoint's formatted output, to help track and compare\n"
			+ "\t      tracefiles from multiple jvm runs.\n"
/*[IF Sidecar18-SE-OpenJ9]*/
			+ "\t      e.g. traceformat 142trcfile /\n"
/*[ELSE]*/
			+ "\t      e.g. java com.ibm.jvm.format.TraceFormat 142trcfile /\n"
/*[ENDIF] Sidecar18-SE-OpenJ9 */
			+ "\t           -datdir /142sdk/jre/lib\n"
			+ "\tthread = only trace information for the specified thread will \n"
			+ "\t      be formatted. Any number of thread IDs can be specified, \n"
			+ "\t      separated by commas.\n"
			+ "\toverridetimezone = specify an integer number of hours to be\n"
			+ "\t      added to the formatted tracepoints (can be negative).\n"
			+ "\t      This option allows the user to override the default time\n"
			+ "\t      zone used in the formatter (GMT)\n"
/*[IF Sidecar18-SE-OpenJ9]*/
			+ "\t      e.g. traceformat 142trcfile /\n"
/*[ELSE]*/
			+ "\t      e.g. java com.ibm.jvm.format.TraceFormat trcfile /\n"
/*[ENDIF] Sidecar18-SE-OpenJ9 */
			+ "\t           -overridetimezone -4\n"
			+ "\tindent = specify indentation at Entry/Exit trace points.\n"
			+ "\t      Default is not to indent.\n"
			+"\thelp 	= display this message and stop.";

	protected static final String header = "                Trace Formatted Data ";

	private static final int FAIL = -1;

	private static final int OK = 0;

	private static String userVMIdentifier = null;

	public static final boolean SUPPRESS_VERSION_WARNINGS = false;

	/**
	 * Main entry point for running the formatter.
	 *
	 * @param args
	 */
	public static void main(String[] args)
	{
		TraceFormat tf = new TraceFormat();
		tf.readAndFormat(args, true);
	}

	/**
	 * Null constructor for the formatter.
	 *
	 * @param None
	 *            <p>
	 *            This is the version used when you run TraceFormat from the
	 *            command line.
	 */
	public TraceFormat()
	{
		super();
		initStatics();
		TraceFormat.outStream = System.out;
		TraceFormat.errStream = new PrintStream(new PipedOutputStream());
	}

	/**
	 * Constructor used to instantiate the formatter programmatically.
	 *
	 * @param args -
	 *            the same as you would specify on the command line.
	 *            <p>
	 *            This version writes to the specified PrintStream.
	 */
	public TraceFormat(PrintStream outStream, String[] args)
	{
		super();
		initStatics();
		TraceFormat.outStream = outStream;
		TraceFormat.errStream = new PrintStream(new PipedOutputStream());

		this.readAndFormat(args, true);

		// Close the formatted output file to avoid "file in use" problems.
		try {
			out.close();
		} catch (IOException ioe) {
			TraceFormat.outStream
					.println("Error closing formatted trace file.");
		} catch (NullPointerException npe) {
			// File wasn't open - no need to report error.
		}
	}

	/**
	 * Initialize the static variables (class variables).
	 * <p>
	 * This is to allow the Trace Formatter to be invoked multiple times from
	 * another program (i.e. the dump formatter) without the undesired
	 * persistence of changes to these variables from run-to-run.
	 */
	private void initStatics()
	{
		threads = new Vector();
		invalidBuffers = 0;
		overallStartSystem = BigInteger.ZERO;
		overallStartPlatform = BigInteger.ZERO;
		first = new BigInteger("FFFFFFFFFFFFFFFF", 16);
		last = BigInteger.ZERO;
		lastWritePlatform = BigInteger.ZERO;
		lastWriteSystem = BigInteger.ZERO;
		timeConversion = BigInteger.ZERO;
		headings = new String(
				"  ThreadID         TP id  Type         TraceEntry ");
		expectedRecords = 0;

		// initialize statics in other classes
		Util.initStatics();
		TraceArgs.initStatics();
		TraceRecord.initStatics();
		MessageFile.initStatics();
	}

	/**
	 * parses command-line args, if in command-line mode, reads the input
	 * file(s) and outputs the result
	 *
	 * @param args
	 *            the command line arguments
	 * @param processFully
	 * 			  if true, the formatter will read the trace file and format the tracepoints into a file
	 * 			  if false, the formatter will prime the trace file so that an external program can iterate
	 * 					over the tracepoints.
	 * @see main
	 */
	public void readAndFormat(String[] args, boolean processFully)
	{
		try {
			traceArgs = new TraceArgs(args); // parse the command line
			// arguments
		} catch (TraceArgs.UsageException usage) {
			TraceFormat.outStream.println(usage.getMessage());
			TraceFormat.outStream.println("TraceFormat " + usageMessage);
			return;
		}

		/* Catch the -help option and terminate. */
		if (TraceArgs.isHelpOnly)
		{
			TraceFormat.outStream.println("TraceFormat " + usageMessage);
			return;
		}

		if (TraceArgs.verbose) {
			errStream = System.err;
		}

		userVMIdentifier = TraceArgs.userVMIdentifier;

		try {
			// create Vector of trace files
			if (getTraceFiles() != 0) {
				return;
			}

			if (verMod >= 0.0) { // check that the header was parsed correctly.
				out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(TraceArgs.outputFile)));

				Util.Debug.println("TraceFormat.verMod = " + verMod);
				if (((TraceArgs.is50orNewer == false) && (verMod < 5.0)) || (TraceArgs.override == true)) {
					readAndFormatOldStyle();
				} else {
					if (processFully){
						readAndFormatNewStyle();
					} else {
						prime();
					}
				}
			}

		} catch (Exception other) {
			other.printStackTrace(TraceFormat.outStream);
		}
	}

	private void readAndFormatOldStyle() throws IOException
	{
		// process the trace records in all the input files
		for (Iterator i = traceFiles.iterator(); i.hasNext();) {
			traceFile = (TraceFile) i.next();
			instantiateMessageFileOldStyle();
			traceFile.traceFileHeader.processTraceBufferHeaders();
		}

		// for each thread sort the trace records
		TraceThread traceThread;
		for (Iterator i = threads.iterator(); i.hasNext();) {
			traceThread = (TraceThread) i.next();
			Collections.sort(traceThread);
		}

		// if -summary then just summarize to stdout
		if (TraceArgs.summary) {
			doSummary(new BufferedWriter(new OutputStreamWriter(
					TraceFormat.outStream)));
			return;
		} else {
			if (doSummary(out) != 0)
				return;
		}

		// write out the formatted entires
		TraceFormat.outStream.println("*** starting formatting of entries");
		out.write(header, 0, header.length());
		out.newLine();
		out.newLine();
		if (Integer.valueOf(Util.getProperty("POINTER_SIZE")).intValue() == 4) {
			headings = "ThreadID TP id  Type         TraceEntry ";
		}
		out.write(Util.getTimerDescription() + headings, 0, headings.length()
				+ Util.getTimerDescription().length());
		out.newLine();

		// the main processing loop
		Merge merge;
		try {
			merge = new Merge(threads);
			String s;
			String eol = System.getProperty("line.separator");
			while ((s = merge.getNextEntry()) != null) {
				// write out the trace entry
				out.write(s + eol);
			}
		} catch (InvalidSpannedRecordException isre) {
			TraceFormat.outStream.println("\n" + isre.getMessage());
			return;
		}

		out.flush();
		TraceFormat.outStream.println("*** formatted ");
		TraceFormat.outStream.println("Formatted output written to file: "
				+ TraceArgs.outputFile);

	}

	private void instantiateMessageFileOldStyle()
	{
		try {
			// this is an old style trace file, we need to insist on having
			// dat files provided
			outStream
					.println("Processing pre 50 trace file with 50 formatter. Searching for the .dat files supplied with the traced vm.");
			outStream
					.println("****Will look in current directory and directory specified by ibm.dg.trc.format environment variable. ***");

			String directory = System.getProperty("ibm.dg.trc.format");
			instantiateMessageFiles(directory);
		} catch (Exception ioe) {
			outStream
					.println("Can't open dat files - you need to copy the dat files from the traced vm to the current directory");
			outStream
					.println(" or use the -datdir flag to tell the formatter which directory it can find them in");
			Util.Debug.println(ioe);
			return;
		}

	}

	private MessageFile tryMessageFileInstantiation(String fileName){
		if (fileName != null){
			try {
				return new MessageFile(fileName);
			} catch (IOException ioe){
				/* can't find the named Trace format file. */
				TraceFormat.outStream.println("*** Unable to open " + fileName + ": " + ioe);
			}
		}
		return null;
	}

	/**
	 * find the most applicable dat file available to this formatter, using the
	 * dirsToSearch list of locations.
	 * @param dirsToSearch - a non null list of directories to search, in the order
	 * to search (i.e. index zero is first location to search).
	 * @param firstFileName - the starting filename.
	 * @return
	 */
	private String findDatFile(String dirsToSearch[], String firstFileName){
		String datfile = null;
		if (dirsToSearch == null || firstFileName == null){
			return null;
		}

		String currentFile = firstFileName;
		while (currentFile != null){
			for (int i = 0; i < dirsToSearch.length; i++){
				String qualFileName = constructFullyQualifiedName(dirsToSearch[i], currentFile);
				File file = new File(qualFileName);
				TraceFormat.errStream.println("*** Looking for " + currentFile + " in " + dirsToSearch[i] + ".");
				if (file.exists()) {
					TraceFormat.outStream.println("*** Found " + file);
					return qualFileName;
				}
			}
			currentFile = traceFile.getNextFormatFileName(currentFile);
		}

		return datfile;
	}

	private void instantiateMessageFiles(String lowestPriorityDefault)
	{
		String dirsToSearch[];
		final String legacyJVMFormatFileName = traceFile.formatFileName();

		TraceFormat.outStream.println("*** Locating formatting template files");

		if (TraceArgs.datFileDirectory == null) {
			/* give current directory preference */
			dirsToSearch = new String[]{".", lowestPriorityDefault};
		} else {
			/* can't hurt to look everywhere :) */
			dirsToSearch = new String[]{TraceArgs.datFileDirectory, ".", lowestPriorityDefault};
		}

		String jvmFormatFilePath = findDatFile(dirsToSearch, legacyJVMFormatFileName);
		if (jvmFormatFilePath == null) {
			/* There was no J9TraceFormat<vm_level>.dat file so use J9TraceFormat.dat.
			 * (Using J9TraceFormat.dat is normal, the other way is a legacy option for
			 * early J9 based JVMs.)
			 */
			jvmFormatFilePath = findDatFile(dirsToSearch, "J9TraceFormat.dat");
		}

		if (jvmFormatFilePath != null) {
			/* we found a suitable dat file */
			messageFile = tryMessageFileInstantiation(jvmFormatFilePath);
		}

		if( messageFile == null ) {
			TraceFormat.outStream.println("*** Could not find a J9TraceFormat.dat file. JVM trace points may be formatted incorrectly.");
		}

		/*
		 * Now load OMRTraceFormat.dat if available.
		 */
		String omrFormatFilePath = findDatFile(dirsToSearch, "OMRTraceFormat.dat");

		if (omrFormatFilePath != null) {
			/* we found a suitable dat file */
			tryMessageFileInstantiation(omrFormatFilePath);
		}

		/*
		 * Also load TraceFormat.dat as it contains the trace points for the class libraries.
		 */
		String filePath = findDatFile(dirsToSearch, "TraceFormat.dat");
		if (filePath != null){
			TraceFormat.outStream.println("*** Loading further formatting templates from " + filePath);
			MessageFile messageFileBase = tryMessageFileInstantiation(filePath); // ibm@77146
			// If TraceFormat.dat is the only file message file,
			// use it for all the formatting.
			if( messageFile == null ) {
				messageFile = messageFileBase;
			}
		}

		if (messageFile == null)
		{
			TraceFormat.outStream.println("Could not find a dat file. No trace point parameters will be formatted.");
		}
	}

	private void instantiateMessageFilesNewStyle()
	{
		String default_dir = System.getProperty("java.home");
		default_dir = default_dir.concat(File.separator).concat("lib");
		instantiateMessageFiles(default_dir);
	}

	private final String constructFullyQualifiedName(String directory,
			String baseName)
	{
		return directory + File.separator + baseName;
	}

	long globalNumberOfBuffers;
	TraceThread[] tempThreadArray;
	TraceThread[] tracedThreads;
	int numberOfThreads;
	BigInteger[] timeStamps;

	private void prime() throws IOException
	{
		/* reinitialize class variables to allow reuse */
		globalNumberOfBuffers = 0;
		tempThreadArray = null;
		tracedThreads = null;
		numberOfThreads = 0;
		timeStamps = null;

		/* process trace files from a 5.0 vm or above */
		long numberOfBuffers = 0;

		int bufferSize = 0;

		Hashtable listOfThreadBuffers = new Hashtable();
		// process the trace files in order
		TraceFormat.outStream
				.println("*** Starting data extraction from binary trace file(s) ");
		for (Iterator i = traceFiles.iterator(); i.hasNext();) {
			traceFile = (TraceFile) i.next();
			instantiateMessageFilesNewStyle();
			/* traceFile.traceFileHeader.processTraceBufferHeaders() */
			long dataStart = traceFile.traceFileHeader.getTraceDataStart();
			bufferSize = traceFile.traceFileHeader.getBufferSize();
			long fileLength = traceFile.length();
			int typeOfTrace = traceFile.traceFileHeader.traceSection
					.getTraceType();
			numberOfBuffers = (fileLength - dataStart) / bufferSize;
			globalNumberOfBuffers += numberOfBuffers;
			if (((fileLength - dataStart) / bufferSize) >= Integer.MAX_VALUE) {
				/* above cast may have lost some data */
				TraceFormat.outStream
						.println("Trace file "
								+ traceFile
								+ " contains more than INT_MAX (" + Integer.MAX_VALUE + ") trace buffers, will only process INT_MAX buffers");
				/*
				 * continue and process the buffers it can (which will
				 * almost certainly be most of them!
				 */
			}
			if (((fileLength - dataStart) % bufferSize) != 0) {
				outStream
						.println("*** TraceFile is truncated, or corrupted, will ignore some incomplete data at the end, but process everything that is available");
				traceFileIsTruncatedOrCorrupt = true;
			}
			if (numberOfBuffers == 0) {
				TraceFormat.outStream.println("\n\n*** " + traceFile
						+ " CONTAINS NO TRACE DATA - skipping file.\n\n");
				continue;
			}

			Util.Debug.println("TP data starts at " + dataStart
					+ ", buffer size is " + bufferSize + ": file contains "
					+ numberOfBuffers + " buffers.");
			/* process the trace files */
			TraceFormat.outStream.println("*** Extracting " + numberOfBuffers + " buffers from " + traceFile);
			for (int j = 0; j < numberOfBuffers; j++) {
				Util.Debug.println("Processing buffer " + j + " at " + (dataStart + j * bufferSize));
				TraceRecord50 traceRecord = new TraceRecord50();
				traceRecord.setTraceType(typeOfTrace);
				Util.Debug.println(" buffer is " + ((typeOfTrace == 0) ? "internal" : "external"));

				/*
				 * just process the headers - actual tracepoint data
				 * will be extracted on the fly
				 */
				traceRecord.processTraceBufferHeader(traceFile, (long)dataStart + (long)j * (long)bufferSize, bufferSize);

				Long threadID = Long.valueOf(traceRecord.getThreadIDAsLong());
				if (listOfThreadBuffers.containsKey(threadID)) {
					TraceThread buffersForThread = (TraceThread) listOfThreadBuffers
							.get(threadID);
					buffersForThread.add(traceRecord);

				} else {
					Vector buffersForThread = new TraceThread(threadID
							.longValue(), traceRecord.getThreadName());
					buffersForThread.addElement(traceRecord);
					threads.addElement(buffersForThread);
					listOfThreadBuffers.put(threadID, buffersForThread);
				}
			}
		}

		/* Set statics to safe values if there were no buffers */
		if (TraceFormat.lastWritePlatform.equals(BigInteger.ZERO)) {
			TraceFormat.lastWritePlatform = overallStartPlatform;
			TraceFormat.lastWriteSystem = overallStartSystem;
			TraceFormat.first = overallStartPlatform;
			TraceFormat.last = overallStartPlatform;
		}

		TraceFormat.outStream.println("*** Sorting buffers");
		TraceThread traceThread;
		for (Iterator i = threads.iterator(); i.hasNext();) {
			/* if the trace file wrapped internally, we need to sort the buffers. */
			traceThread = (TraceThread) i.next();
			Collections.sort(traceThread);
		}

		// if -summary then just summarize to stdout
		if (TraceArgs.summary) {
			doSummary(new BufferedWriter(new OutputStreamWriter(
					TraceFormat.outStream)));
			return;
		} else {
			if (doSummary(out) != 0) {
				TraceFormat.outStream
						.println("*** Problem printing summary to file - may be incomplete ... continuing");
				// return;
			}
		}

		// write out the header info
		TraceFormat.outStream
				.println("*** Starting formatting of entries into text file "
						+ TraceArgs.outputFile);
		out.write(header, 0, header.length());
		out.newLine();
		out.newLine();

		int ptrWidth = Integer.valueOf(Util.getProperty("POINTER_SIZE")).intValue();

		StringBuffer blanks = new StringBuffer("");
		for (int i = 0; userVMIdentifier!= null && i <= userVMIdentifier.length(); i++) {
			blanks.append(" ");
		}

		if (ptrWidth == 4) {
			headings = blanks
					+ "  ThreadID       TP id     Type        TraceEntry ";
		} else {
			headings = blanks
					+ "  ThreadID               TP id     Type        TraceEntry ";
		}

		out.write(Util.getTimerDescription() + headings, 0, headings.length()
				+ Util.getTimerDescription().length());
		out.newLine();

		/* worm through the tracefile printing out the tracepoints */
		tempThreadArray = new TraceThread[0];
		tracedThreads = (TraceThread[]) threads.toArray(tempThreadArray);
		numberOfThreads = tracedThreads.length;
		System.out.println("*** Number of traced threads = " + numberOfThreads);
		timeStamps = new BigInteger[numberOfThreads];
		populateTimeStamps(tracedThreads, timeStamps, numberOfThreads);
		primed = true;
	}

	private void readAndFormatNewStyle() throws IOException
	{
		if (!primed){
			prime();
		}
		double onetenthofbuffers = (double) globalNumberOfBuffers / 10.0;
		int nextTenth = 0;
		int numberOfBuffersProcessed = 0;
		long threadID, lastThreadID = 0;
		TracePoint tp;
		TraceThread tthread;
		int tracePointsFormatted = 0;
		StringBuffer tempTPString;

		while ((tp = findNextTracePoint(tracedThreads, timeStamps,
				numberOfThreads)) != null) {
			if (!tp.isNormalTracepoint()) {
				/* let the tracepoint give us it's text */
				out.write(tp.toString());
				out.newLine();

				continue;
			}

			numberOfBuffersProcessed = TraceThread.getBuffersProcessed();
			tempTPString = new StringBuffer();

			/* output some friendly progress information for the user */
			if (numberOfBuffersProcessed >= (nextTenth * onetenthofbuffers)) {
				outStream.print((nextTenth * 10) + "% ");
				nextTenth++;
			}

			tthread = getCurrentTraceThread(tracedThreads);
			Message msg = MessageFile.getMessageFromID(tp.getComponentName(),
					tp.getTPID());
			tracePointsFormatted++;

			threadID = tp.getThreadID();
			if (Util.findThreadID(Long.valueOf(threadID))) {
				String formattedTime = tp.getFormattedTime();
				tempTPString.append(formattedTime);

				/* prepare the tp for the "change of thread" star */
				if (threadID != lastThreadID) {
					tp.setIsChangeOfThread(true);
				}
				lastThreadID = threadID;

				tempTPString.append(tp.toString());
				tempTPString.append(" ");

				if (msg == null) {
					Util.Debug.println("No message for tracepoint ["
							+ tp.getComponentName() + "." + tp.getTPID() + "]");
				} else {
					tempTPString.append(tp.getType());
					int type = tp.getTypeAsInt();
					/* indentation handled here */
					/* Check if indented format requested ibm@97470 */
					if (TraceArgs.indent) {
						if (type == TraceRecord.ENTRY_TYPE
								|| type == TraceRecord.ENTRY_EXCPT_TYPE) {
							tthread.indent();
						}

						for (int x = 0; x < tthread.getIndent(); x++) {
							tempTPString.append(" ");
						}

						if (type == TraceRecord.EXIT_TYPE
								|| type == TraceRecord.EXIT_EXCPT_TYPE) {
							tthread.outdent();
						}
					}

					String formattedData = tp.getFormattedParameters();
					tempTPString.append(formattedData);

					if (TraceArgs.debug){
						/* tie the formatted tracepoint to the binary file location */
						tempTPString.append(" " + tp.from());
					}
				}

				out.write(tempTPString.toString());
				out.newLine();
			}
			populateTimeStamps(tracedThreads, timeStamps, numberOfThreads);
		}

		if (nextTenth < 11) {
			/*
			 * this can occur because, for example, all of the final 10%
			 * of buffers were empty.
			 * Any buffers skipped will be reported under the
			 * numberOfBuffersProcessed check below
			 */
			outStream.print("... 100%");
		}

		TraceFormat.outStream.print("\n");
		TraceFormat.outStream.println("*** Number of formatted tracepoints = "
				+ tracePointsFormatted);

		if (numberOfBuffersProcessed < globalNumberOfBuffers) {
			TraceFormat.outStream.println("\n" + numberOfBuffersProcessed + "/"
					+ globalNumberOfBuffers
					+ " buffers processed successfully\n");
		}

		if (traceFileIsTruncatedOrCorrupt) {
			out
					.write(" NOTE - PROBLEMS WERE ENCOUNTERED PROCESSING THE TRACE FILE(S), MOST LIKELY DUE TO TRACE FILE CORRUPTION OR TRUNCATION");
			out.newLine();
			out
					.write(" THE CONTENT OF THIS FORMATTED FILE COULD THEREFORE BE TRUNCATED OR CORRUPTED ALSO - REFER TO FORMATTER OUTPUT FOR FURTHER DETAILS");
			out.newLine();
		}

		out.flush();

		if (lostRecordCount > 0) {
			TraceFormat.outStream.println("*** "+lostRecordCount+" buffers were discarded during trace data generation");
		}

		TraceFormat.outStream.println("*** Formatting complete");
		TraceFormat.outStream.println("*** Formatted output written to file: "
				+ TraceArgs.outputFile);
	}

	/*
	 * Summarize the trace information based on the main trace file ( in case
	 * there more than one )
	 */
	protected int doSummary(BufferedWriter out) throws IOException
	{
		TraceThread traceThread;
		out.write("                Trace Summary");
		out.newLine();
		out.newLine();
		TraceFile tf = (TraceFile)traceFiles.firstElement();
		tf.traceFileHeader.summarize(out);
		out.write("Active Threads :");
		out.newLine();

		// Write out list of threads in trace files
		for (Iterator i = threads.iterator(); i.hasNext();) {
			traceThread = (TraceThread) i.next();
			out.write(Util.SUM_TAB
					+ Util.formatAsHexString(traceThread.threadID));
			out.write("  ");
			out.write(traceThread.threadName);
			out.newLine();
		}
		out.newLine();

		if (TraceFormat.verMod >= 1.1) {
			BigInteger spanPlatform = lastWritePlatform
					.subtract(TraceFormat.overallStartPlatform);
			BigInteger spanSystem = lastWriteSystem
					.subtract(TraceFormat.overallStartSystem);
			Util.Debug.println("lastWritePlatform:    " + lastWritePlatform);
			Util.Debug.println("lastWriteSystem:      " + lastWriteSystem);
			Util.Debug.println("overallStartPlatform: " + overallStartPlatform);
			Util.Debug.println("overallStartSystem:   " + overallStartSystem);
			Util.Debug.println("spanPlatform:         " + spanPlatform);
			Util.Debug.println("spanSystem:           " + spanSystem);

			// Check the start time has been set, if not this calculation is not valid.
			if (overallStartSystem.compareTo(BigInteger.ZERO) != 0 &&
				spanSystem.compareTo(BigInteger.ZERO) != 0) {
				timeConversion = spanPlatform.divide(spanSystem);
			} else {
				timeConversion = BigInteger.ONE;
			}

			Util.Debug.println("timeConversion:         " + timeConversion);

			out.write("JVM started      : "
					+ Util.getFormattedTime(TraceFormat.overallStartPlatform));
			out.newLine();
			out.newLine();
			out.write("Last buffer write: "
					+ Util.getFormattedTime(lastWritePlatform));
			out.newLine();
			out.newLine();
		}

		String fstring = "First tracepoint:  " + Util.getFormattedTime(first);
		String lstring = "Last tracepoint :  " + Util.getFormattedTime(last);

		out.write(fstring, 0, fstring.length());
		out.newLine();
		out.newLine();
		out.write(lstring, 0, lstring.length());
		out.newLine();
		out.newLine();
		out.newLine();
		out.flush();

		return (OK);
	}

	/*
	 * record the system time of the first trace record.
	 */
	final static void setStartSystem(BigInteger startSystem)
	{
		if (overallStartSystem.equals(BigInteger.ZERO)) {
			overallStartSystem = startSystem;
		}
		if (overallStartSystem.compareTo(startSystem) == -1) {
			overallStartSystem = startSystem;
		}
	}

	/*
	 * record of the platform time of the first trace record.
	 */
	final static void setStartPlatform(BigInteger startPlatform)
	{
		if (overallStartPlatform.equals(BigInteger.ZERO)) {
			overallStartPlatform = startPlatform;
		}
		if (overallStartPlatform.compareTo(startPlatform) == -1) {
			overallStartPlatform = startPlatform;
		}
	}

	/*
	 * Create a Vector containing all the trace input files.
	 */
	final int getTraceFiles()
	{
		int RADIX = 36;
		int hash = TraceArgs.traceFile.indexOf("#");

		if (hash == -1) { // no # char in name of input file
			try {
				traceFiles.addElement(new TraceFile(traceArgs.traceFile, "r"));
			} catch (FileNotFoundException e) {
				TraceFormat.outStream.println("Trace file " + traceArgs.traceFile + " not found");
				return (FAIL);
			} catch (Exception e) {
				e.printStackTrace(TraceFormat.errStream);
				return (FAIL);
			}
		} else {
			int i = 0;
			char index = (char) 0;

			for (i = 0; i < RADIX; i++) {
				index = Integer.toString(i, RADIX).toUpperCase().charAt(0);
				try {
					traceFiles.addElement(new TraceFile(TraceArgs.traceFile
							.replace('#', index), "r"));
				} catch (Exception e) {
					// e.printStackTrace();
					Util.Debug.println("TraceFormat: generations found " + i);
					break;
				}
			}
			if (i == 0) {
				TraceFormat.outStream.println("Trace file is missing");
				return (FAIL);
			}
			if (i != generations) {
				Util.Debug.println("TraceFormat: generations = " + generations);
				TraceFormat.outStream.println("Trace file "
						+ TraceArgs.traceFile.replace('#', index)
						+ " is missing");
			}
			TraceFormat.outStream.println("Processing "
					+ (i != generations ? Integer.toString(i) : "all")
					+ " of the " + generations
					+ " generations specified at runtime");
		}

		return (OK);
	}

	/*
	 * Set the number of generations specified at run time.
	 */
	final static void setGenerations(int gen)
	{
		generations = gen;
		return;
	}

	/*
	 * return the user supplied vm identifier if present
	 */
	public static String getUserVMIdentifier()
	{
		return userVMIdentifier;
	}

	private boolean populateTimeStamps(TraceThread[] tracedThreads,
			BigInteger[] timeStamps, int numberOfThreads)
	{
		for (int i = 0; i < numberOfThreads; i++) {
			timeStamps[i] = tracedThreads[i].getTimeOfNextTracePoint();
		}
		return true;
	}

	private int tracedThreadWithNewestTracePoint = -1;

	private TraceThread getCurrentTraceThread(TraceThread[] tracedThreads)
	{
		if (tracedThreadWithNewestTracePoint < 0) {
			return null;
		} else {
			return tracedThreads[tracedThreadWithNewestTracePoint];
		}
	}

	public com.ibm.jvm.trace.TraceThread[] getTraceThreads(){
		return tracedThreads;
	}

	/* allow external iterators access to the next tracepoint */
	public com.ibm.jvm.trace.TracePoint getNextTracePoint(){
		TracePoint tp = findNextTracePoint(tracedThreads, timeStamps, numberOfThreads);
		populateTimeStamps(tracedThreads, timeStamps, numberOfThreads);
		return tp;
	}

	private TracePoint findNextTracePoint(TraceThread[] tracedThreads,
			BigInteger[] timeStamps, int numberOfThreads)
	{
		tracedThreadWithNewestTracePoint = 0;
		if (timeStamps.length == 0 || tracedThreads == null
				|| numberOfThreads == 0) {
			/* this means all the tracedThreads were empty! */
			tracedThreadWithNewestTracePoint = -1;
			return null;
		}
		BigInteger tempBI = timeStamps[0];
		/* find the lowest (i.e. oldest) timeStamp in the timeStamp array */
		for (int i = 1; i < numberOfThreads; i++) {
			if (tempBI == null) {
				tempBI = timeStamps[i];
				tracedThreadWithNewestTracePoint = i;
			} else if (timeStamps[i] == null) {
				/* skip this one - it can't be newer */
			} else {
				if (tempBI.compareTo(timeStamps[i]) > 0) {
					tempBI = timeStamps[i];
					tracedThreadWithNewestTracePoint = i;
				}
			}
		}

		if (tempBI == null) {
			/* this means all the tracedThreads were empty! */
			tracedThreadWithNewestTracePoint = -1;
			return null;
		} else {
			return tracedThreads[tracedThreadWithNewestTracePoint]
					.getNextTracePoint();
		}
	}

	public com.ibm.jvm.trace.TraceFileHeader getTraceFileHeader(){
		TraceFile tf = (TraceFile)traceFiles.firstElement();
		if (tf == null){
			return null;
		} else {
			return tf.getHeader();
		}
	}
}
