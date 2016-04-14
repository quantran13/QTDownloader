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

public class Main {

	public static void main(String[] args) {
		if (args.length != 1) {
			printUsage();
			return;
		}
		
		String url = args[0];
		int partsCount = 8;
		
		Download newDownload = new Download(url, partsCount);

		try {
			newDownload.start();
		} catch (InterruptedException | RuntimeException ex) {
			System.err.println(ex.getMessage());
		} catch (ConnectException ex) {
			System.err.println("Failed to connect to the given URL!");
			System.err.println("Check your internet connection.");
		} catch (IOException ex) {
			if (ex instanceof MalformedURLException)
				System.err.println("The given URL is invalid!");
			else {
				System.err.println("Failed to open the output file!");
			}
		}
	}

	private static void printUsage() {
		System.err.println("Usage: \n");
	}

}
