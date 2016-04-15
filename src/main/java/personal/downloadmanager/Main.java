/**
 * Class: Main.java
 *
 * @author quan
 *
 */
package personal.downloadmanager;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

public class Main {
	
	public static int partsCount;
	public static String url;

	/**
	 *
	 * @param args
	 * @throws java.lang.InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		if (args.length != 1) {
			printUsage();
			return;
		}
		
		url = args[0];
		partsCount = 8;
		
		// Create a Progress object to keep track of the download
		Progress progress = new Progress();
		
		// Start new download with the given URL
		DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		Date date = new Date();
		System.out.println("--- " + dateFormat.format(date) + " ---");
		System.out.println("Downloading from: " + url);
	
		Download newDownload = new Download(url, partsCount, progress);

		// Start the download.
		Instant start = Instant.now();
		newDownload.startThread();

		// Verify URL
		System.out.println("Sending HTTP request...");
		synchronized(progress) {
			while (progress.mURLVerifyResult.responseCode == 0 
					&& progress.ex == null)
				progress.wait();

			if (progress.ex == null) {
				System.out.println("Response code: " + 
					progress.mURLVerifyResult.responseCode);
				System.out.println("Fize size: " + 
					progress.mURLVerifyResult.contentLength + " bytes.");
			} else {
				printErrorMessage(progress.ex);
			}
		}
		
		// Wait for the download to finish
		Instant downloadFinish = null;
		
		synchronized(progress) {
			while (!progress.downloadFinish && progress.ex == null)
				progress.wait();
			
			if (progress.ex == null) {
				downloadFinish = Instant.now();
				double downloadTime = ((double) (Duration.between(start, 
					downloadFinish).toMillis())) / 1000;
				
				System.out.println("\nTotal download time: " + downloadTime);
			} else {
				printErrorMessage(progress.ex);
			}
		}
		
		// Wait for the parts to finish joining.
		Instant joinFinish;
		
		synchronized(progress) {
			while (!progress.joinPartsFinish && progress.ex == null) {
				progress.wait();
			}
			
			if (progress.ex == null) {
				joinFinish = Instant.now();
				double joinTime = ((double) (Duration.between(downloadFinish,
					joinFinish).toMillis())) / 1000;
				
				System.out.println("Total join time: " + joinTime);
			} else {
				printErrorMessage(progress.ex);
			}
		}
		
		// Wait for the main download thread to end.
		try {
			newDownload.joinThread();
		} catch (InterruptedException ex) {
			System.err.println("Download thread interrupted: " + ex.getMessage());
		}
		
		// Print the current time
		date = new Date();
		System.out.println("Finished downloading!");
		System.out.println("\n--- " + dateFormat.format(date) + " ---");
	}

	private static void printUsage() {
		System.err.println("Usage: java -jar dm.jar <url>\n");
	}
	
	private static void printErrorMessage(Exception ex) {
		/*
		 * Print the appropriate error message from the exception caught.
		 */
		if (ex instanceof MalformedURLException) {
			System.err.println("\nInvalid URL: " + ex.getMessage());
		} else if (ex instanceof ConnectException) {
			ConnectException connectException = (ConnectException) ex;
			System.err.println("\nFailed to connect to the given URL: " + 
				connectException.getMessage());
			System.err.println("\nCheck your internet connection or URL again.");
		} else if (ex instanceof IOException) {
			System.err.println("\nFailed to open the output file: " + 
				ex.getMessage());
		} else if (ex instanceof InterruptedException) {
			System.err.println("\nOne of the thread was interrupted: " + 
				ex.getMessage());
		} else if (ex instanceof RuntimeException) {
			System.err.println(ex.getMessage());
		}
		
		/*
		 * Exit the program.
		 */
		System.err.println("\nExiting!");
		System.exit(0);
	}
	
}
