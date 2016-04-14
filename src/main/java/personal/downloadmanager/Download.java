/**
 * Class: DownloadDemo.java
 *
 * @author quan
 *
 */
package personal.downloadmanager;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

/**
 *
 * @author quan
 */
public class Download {

	private final String mUrl;
	private final int mPartsCount;

	/**
	 * Constructor for the Download class which takes an URL string as parameter
	 *
	 * @param urlString
	 * @param partsCount
	 */
	public Download(String urlString, int partsCount) {
		mUrl = urlString;
		mPartsCount = partsCount;
	}

	/**
	 * Start downloading from the given URL.
	 * 
	 * @throws IOException If failed to open the output file.
	 * @throws MalformedURLException If the given URL is invalid.
	 * @throws InterruptedException If the downloading threads are interrupted.
	 * @throws ConnectException If failed to connect to the given URL.
	 */
	public void start() throws InterruptedException, MalformedURLException, IOException, ConnectException {
		// Get the file name and create the URL object
		String fileName = new File(mUrl).getName();
		URL url = new URL(mUrl);

		// Check the validity of the URL
		HttpResult result = checkURLValidity(url);
		long contentSize = result.contentLength;
		int responseCode = result.responseCode;
		long partSize = contentSize / mPartsCount;
		
		if (contentSize == -1 || responseCode != 200)
			throw new RuntimeException("Server responsed with the error code: "
			                           + responseCode + ".");

		// Start threads to download.
		Instant start = Instant.now();
		ArrayList<DownloadThread> downloadParts;

		try {
			downloadParts = startDownloadThreads(url,
				contentSize, mPartsCount);
		} catch (RuntimeException ex) {
			throw ex;
		}

		// Wait for the threads to finish downloading
		for (int i = 0; i < downloadParts.size(); i++) {
			downloadParts.get(i).joinThread();
			if (downloadParts.get(i).mDownloadedSize != partSize) {
				throw new RuntimeException("Download incompleted at part "
					+ (i + 1));
			}
		}

		// Calculte the time it took to download all mPartsCount
		Instant stop = Instant.now();
		double t = (double) ((Duration.between(start, stop).toMillis()));
		System.out.println("Parts downloaded in: " + t);

		// Join the mPartsCount together
		joinDownloadedParts(fileName, downloadParts);
		
		// Delete part files
		try {
			for (int i = 0; i < downloadParts.size(); i++) {
				String partName = fileName + ".part" + (i+1);
				Path filePath = Paths.get(partName);
				Files.deleteIfExists(filePath);
			}
		} catch (IOException ex) { 
			// If failed to delete then just ignore the exception.
			// What can we do?
		}

		// Time
		Instant done = Instant.now();
		double time = (double) ((Duration.between(stop, done).toMillis()));
		System.out.println("Parts joined in:: " + time);
	}

	/**
	 * Check the validity of the given URL.
	 *
	 * @param urlString The given URL.
	 * @return The content size from the requested URL. 
	 * If -1 then the response from the server is not success.
	 * 
	 * @throws ConnectException if failed to connect to the given URL.
	 */
	private HttpResult checkURLValidity(URL url) throws ConnectException {
		// Create new connection from the given url
		try {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			// Connect to the created connection.
			conn.setRequestMethod("GET");
			conn.connect();

			// Check for the response code
			int responseCode = conn.getResponseCode();
			long contentSize = conn.getContentLengthLong();

			// Return the content size
			HttpResult result = new HttpResult(responseCode, contentSize);

			return result;
		} catch (IOException ex) {
			throw new ConnectException(ex.getMessage());
		}
	}

	/**
	 * Start the given number of threads to download from the given URL.
	 *
	 * @param url The URL object containing the download URL.
	 * @param contentSize The size of the file being downloaded.
	 * @param partCount The number of download mPartsCount.
	 * 
	 * @return An ArrayList of downloading thread objects.
	 */
	public ArrayList<DownloadThread> startDownloadThreads(URL url, long contentSize, int partCount) {
		long partSize = contentSize / partCount;
		ArrayList<DownloadThread> downloadThreadsList = new ArrayList<>(partCount);

		for (int i = 0; i < partCount; i++) {
			long beginByte = i * partSize;
			long endByte;
			if (i == partCount - 1) {
				endByte = contentSize - 1;
			} else {
				endByte = (i + 1) * partSize - 1;
			}

			DownloadThread downloadThread = new DownloadThread(url, beginByte, endByte, i + 1);
			downloadThreadsList.add(downloadThread);
			downloadThreadsList.get(i).startDownload();
		}

		return downloadThreadsList;
	}
	
	/**
	 * Join the downloaded parts together into the file with the given file name.
	 * 
	 * @param fileName Name of output file.
	 * @param downloadParts An ArrayList of download threads.
	 * 
	 * @throws java.io.IOException if failed to open the output file.
	 */
	public void joinDownloadedParts(String fileName, ArrayList<DownloadThread> downloadParts) throws IOException {
		try (RandomAccessFile mainFile = new RandomAccessFile(fileName, "rw")) {
			FileChannel mainChannel = mainFile.getChannel();
			
			for (int i = 0; i < downloadParts.size(); i++) {
				long partSize = downloadParts.get(i).mDownloadedSize;
				String partName = fileName + ".part" + (i + 1);
				
				try (RandomAccessFile partFile = new RandomAccessFile(partName, "rw")) {
					FileChannel partFileChannel = partFile.getChannel();
					mainChannel.transferFrom(partFileChannel, i * partSize, partSize);
				}
			}
		}
	}

}