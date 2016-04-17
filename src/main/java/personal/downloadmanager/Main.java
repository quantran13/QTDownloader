/**
 * Class: Main.java
 *
 * @author quan
 *
 */
package personal.downloadmanager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Scanner;

public class Main {
	
	public static int partsCount;
	public static String url;
	
	private static final String PROGRAM_DIR = System.getenv("HOME") 
	                                         + "/.QTDownloadManager";
	private static final String DOWNLOADED_LIST_FILENAME = PROGRAM_DIR 
		                                                   + "/.filelist.csv";

	/**
	 *
	 * @param args
	 * @throws java.lang.InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		if (args.length != 1 && args.length != 2) {
			printUsage();
			return;
		}
		
		url = args[0];
		partsCount = 8;
		
		// Check if the file has been downloaded or not
		String fileName = new File(url).getName();
		
		try {
			FileReader downloadedFileList = new FileReader(DOWNLOADED_LIST_FILENAME);
			BufferedReader br = new BufferedReader(downloadedFileList);
			String line;
			
			try {
				while ((line = br.readLine()) != null) {
					String[] wordList = line.split(", ");
					
					if (url.equals(wordList[1])) {
						System.out.print("You downloaded from this URL. " +
							"Do you want to download again? (y/n)");
						
						char answer = 0;
						Scanner reader = new Scanner(System.in);
						while (answer != 'y' && answer != 'Y' 
							   && answer != 'n' && answer != 'N') {
							answer = reader.next().charAt(0);
						}
						
						if (answer == 'n' || answer == 'N') {
							downloadedFileList.close();
							br.close();
							reader.close();
							
							return;
						}
					}
				}
			} catch (IOException ex) {
				// If there's an error reading the file list then 
				// just ignore the exception, and assume the file hasn't
				// been downloaded before
			}
		} catch (FileNotFoundException ex) {
			// Again, if the file does not exist then ignore the exception.
			// Assume the file being downloaded hasn't been downloaded before.
		}
		
		
		// Create a Progress object to keep track of the download
		Progress progress = new Progress();
		
		// Start new download with the given URL
		DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		Date date = new Date();
		System.out.println("--- " + dateFormat.format(date) + " ---\n");
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
					readableFileSize(progress.mURLVerifyResult.contentLength));
			} else {
				printErrorMessage(progress.ex);
			}
		}
		
		System.out.println();
		
		// Wait for the download to finish
		Instant downloadFinish = null;
		
		synchronized(progress) {
			while (!progress.downloadFinish && progress.ex == null)
				progress.wait();
			
			if (progress.ex == null) {
				downloadFinish = Instant.now();
				double downloadTime = ((double) (Duration.between(start, 
					downloadFinish).toMillis())) / 1000;
				
				System.out.println("\n\nTotal download time: " + downloadTime);
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
		
		
		// Save the download to the downloaded file list
		try (FileWriter downloadFile = new FileWriter(DOWNLOADED_LIST_FILENAME, true)) {
			try (BufferedWriter bw = new BufferedWriter(downloadFile)) {
				String line = fileName + ", " + url + ", " 
				              + progress.downloadedCount + "\n";
				bw.write(line);
			}
		} catch (IOException ex) {
			System.out.println("Cannot open downloaded file list: " + 
				ex.getMessage());
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
	
	private static String readableFileSize(long bytes) {
		String[] fileSizeUnits = {"bytes", "KB", "MB", "GB", "TB", "PB", "EB"};
		String result = "";
		
		double size = bytes;
		int unit = 0;
		while (size > 1024 && unit < fileSizeUnits.length) {
			size = size / 1024;
			unit++;
		}
		
		size = (double) Math.round(size * 100) / 100;
		result += String.valueOf(size) + " " + fileSizeUnits[unit];
		
		return result;
	}
	
}
