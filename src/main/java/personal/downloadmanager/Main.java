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
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
	
	public static int partsCount;
	public static String mURL;
	
	private static int lineNumber;
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
		
		mURL = args[0];
		partsCount = 8;
		
		// Check if the file has been downloaded or not
		String fileName = new File(mURL).getName();
		boolean downloaded = false;
		boolean resume = false;
		
		HashMap<String, DownloadSession> downloadSessionList = null;
		
		try {
			downloadSessionList = fileDownloaded();
			
			for (Map.Entry<String, DownloadSession> entry : downloadSessionList.entrySet()) {
				String url = entry.getKey();
				
				if (url.equals(mURL)) {
					downloaded = true;
					
					DownloadSession ds = entry.getValue();
					long downloadedSize = ds.getDownloadedSize();

					if (downloadedSize == -1) {
						System.out.print("Your previous download from this URL "
						+ "was interrupted. ");
						System.out.print("Do you want to continue downloading? (y/n)");

						char answer = 0;
						Scanner reader = new Scanner(System.in);
						while (answer != 'y' && answer != 'Y' 
							   && answer != 'n' && answer != 'N') {
							answer = reader.next().charAt(0);
						}

						if (answer == 'y' || answer == 'Y') {
							resume = true;
						}
						
						break;
					} else {
						System.out.print("You downloaded from this URL. " +
							"Do you want to download again? (y/n)");
						
						char answer = 0;
						Scanner reader = new Scanner(System.in);
						while (answer != 'y' && answer != 'Y' 
							   && answer != 'n' && answer != 'N') {
							answer = reader.next().charAt(0);
						}

						if (answer == 'n' || answer == 'N')
							return;
						}
					
					break;
				}
			}
		} catch (IOException ex) {
			printErrorMessage(ex);
		}
		
		// If failed to read from the download list file
		// create a new hashmap for the download sessions list
		if (downloadSessionList == null)
			downloadSessionList = new HashMap<>();
		
		DownloadSession ds;
		if (downloaded) {
			ds = downloadSessionList.get(mURL);
			ds.setDownloadSize(-1);
		} else {
			ds = new DownloadSession(fileName, mURL, -1);
			downloadSessionList.put(mURL, ds);
		}
		
		try {
			writeInfo(downloadSessionList);
		} catch (IOException ex) {
			printErrorMessage(ex);
		}
		
		// Create a Progress object to keep track of the download
		Progress progress = new Progress();
		
		// Start new download with the given URL
		DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
		Date date = new Date();
		System.out.println("--- " + dateFormat.format(date) + " ---\n");
		System.out.println("Downloading from: " + mURL);
	
		Download newDownload = new Download(mURL, partsCount, progress, resume);

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
					Utility.readableFileSize(progress.mURLVerifyResult.contentLength));
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
			printErrorMessage(ex);
		}
		
		
		// Save the download to the downloaded file list
		ds.setDownloadSize(progress.downloadedCount);
		try {
			writeInfo(downloadSessionList);
		} catch (IOException ex) {
			printErrorMessage(ex);
		}
		
		
		// Print the current time
		date = new Date();
		System.out.println("Finished downloading!");
		System.out.println("\n--- " + dateFormat.format(date) + " ---");
	}

	/**
	 * Print the usage.
	 */
	private static void printUsage() {
		System.err.println("Usage: java -jar dm.jar <url>\n");
	}
	
	/**
	 * Print the appropriate error message for the given exception.
	 * 
	 * @param ex The exception whose message is to be printed.
	 */
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
			System.err.println("\n" + ex.getMessage());
		}
		
		/*
		 * Exit the program.
		 */
		System.err.println("\nExiting!");
		System.exit(0);
	}
	
	/**
	 * Check if the file being downloaded has been downloaded before or not.
	 * 
	 * @return A hashmap containing the information of the downloaded files,
	 * with the URL as key and an object of type DownloadSession as value.
	 * 
	 * @throws IOException If failed to open the downloaded file list.
	 */
	private static HashMap<String, DownloadSession> fileDownloaded() throws IOException {
		HashMap<String, DownloadSession> downloadedList = new HashMap<>();
		
		try (FileReader downloadedFileList = new FileReader(DOWNLOADED_LIST_FILENAME);
				BufferedReader br = new BufferedReader(downloadedFileList)) {
			String line;
			
			try {
				while ((line = br.readLine()) != null) {
					String[] wordList = line.split(",");
					
					if (wordList.length == 3) {
						String fileName = wordList[0];
						String downloadUrl = wordList[1];
						long downloadedSize = Long.parseLong(wordList[2]);
						
						DownloadSession ds = new DownloadSession(fileName, 
							downloadUrl, downloadedSize);
						
						downloadedList.put(downloadUrl, ds);
					} else if (wordList.length == 2) {
						String fileName = wordList[0];
						String downloadUrl = wordList[1];
						long downloadedSize = -1;
						
						DownloadSession ds = new DownloadSession(fileName, 
							downloadUrl, downloadedSize);
						
						downloadedList.put(downloadUrl, ds);
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
		
		return downloadedList;
	}

	/**
	 * 
	 * @param sessionList
	 * @throws IOException 
	 */
	private static void writeInfo(HashMap<String, DownloadSession> sessionList) throws IOException {
		String tmpFileName = ".filelist.csv";
		
		try (FileWriter tmpFile = new FileWriter(tmpFileName, false);
				BufferedWriter wr = new BufferedWriter(tmpFile)) {
			for (Map.Entry<String, DownloadSession> entry : sessionList.entrySet()) {
				DownloadSession session = entry.getValue();
				String url = entry.getKey();
				String fileName = session.getFileName();
				long downloadedSize = session.getDownloadedSize();
				
				String line = fileName + "," + url + ",";
				line += String.valueOf(downloadedSize) + "\n";
				
				wr.write(line);
			}
		}
	}
}
