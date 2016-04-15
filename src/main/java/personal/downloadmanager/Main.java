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
import java.time.Instant;
import java.util.Date;

public class Main {

	public static void main(String[] args) throws InterruptedException {
		if (args.length != 1) {
			printUsage();
			return;
		}
		
		String url = args[0];
		int partsCount = 7;
		
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
				System.out.println(progress.ex.getMessage());
			}
		}
		
		// Wait for the download to finish
		try {
			newDownload.joinThread();
		} catch (InterruptedException ex) {
			System.err.println("Download thread interrupted: " + ex.getMessage());
		}
		
		// Print the current time
		date = new Date();
		System.out.println("\n--- " + dateFormat.format(date) + " ---");
	}

	private static void printUsage() {
		System.err.println("Usage: java -jar dm.jar <url>\n");
	}
	
}
