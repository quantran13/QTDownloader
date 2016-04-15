/**
 * Class: Download.java
 *
 * @author quan
 *
 */
package personal.downloadmanager;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author quan
 */
public class DownloadThread implements Runnable {

	private Thread mThread;
	private long mStartByte;
	private long mEndByte;
	private long mPartSize;
	private int mPart;
	private URL mUrl;
	private long mDownloadedSize;
	
	public final String mFileName;

	/**
	 * Construct a download object with the given URL and byte range to downloads
	 *
	 * @param url The URL to download from.
	 * @param startByte The starting byte.
	 * @param endByte The end byte.
	 * @param partSize The size of the part needed to be downloaded.
	 * @param part The part of the file being downloaded.
	 */
	public DownloadThread(URL url, long startByte, long endByte, long partSize, int part) {
		if (startByte >= endByte) {
			throw new RuntimeException("The start byte cannot be larger than "
				+ "the end byte!");
		}

		mStartByte = startByte;
		mEndByte = endByte;
		mPartSize = partSize;
		mUrl = url;
		mPart = part;
		mDownloadedSize = 0;
		
		// Get the file name.
		mFileName = "." + (new File(mUrl.toExternalForm()).getName() + ".part" 
			+ mPart);

		// Initialize the thread
		mThread = new Thread(this, "Part #" + part);
	}

	/**
	 * Start the thread to download.
	 */
	public void startDownload() {
		mThread.start();
	}

	/**
	 * Wait for the thread to finish.
	 *
	 * @throws java.lang.InterruptedException If join() failed.
	 */
	public void joinThread() throws InterruptedException {
		mThread.join();
	}

	/**
	 * Get the HTTP Connection with the download URL.
	 *
	 * @return The HTTP connection with the download URL.
	 * @throws java.net.MalformedURLException if the given URL is invalid.
	 * @throws IOException if failed to connect to the given URL.
	 */
	public HttpURLConnection getHttpConnection() throws IOException {
		// Connect to the URL
		HttpURLConnection conn = (HttpURLConnection) mUrl.openConnection();

		String downloadRange = "bytes=" + mStartByte + "-" + mEndByte;
		conn.setRequestProperty("Range", downloadRange);
		conn.connect();

		// Return the connection.
		return conn;
	}

	/**
	 * Write the data from the given connection to file.
	 *
	 * @param conn
	 * @throws java.io.IOException
	 */
	public void downloadToFile(HttpURLConnection conn) throws IOException {
		// Get the input stream.
		InputStream is = conn.getInputStream();
		
		// Size of the chunk of data to be downloaded and written to the 
		// output file at a time.
		int chunkSize = 8192;

		try (DataInputStream dataIn = new DataInputStream(is)) {
			// Get the file's length.
			long contentLength = conn.getContentLengthLong();
			
			mDownloadedSize = 0;
			
			/*
			 * The first method of downloading has the same performance as the
			 * second one. However I prefer the second one (partly because 
			 * initially I thought it would be better), so I used the second one.
			 * Feel free to improve the algorithm, and if you find a faster way
			 * please let me know :)
			 *
			 * Btw, the first method is commented out below.
			 */
			 
			/*
			// Read 1 byte at a time until the data array is full and write the
			// data to the output file.
			int mainArraySize;
			int subArraySize = 0;
			boolean useSubArray;
			long arrayCount = contentLength / chunkSize;
			
			if (contentLength <= chunkSize) {
				mainArraySize = (int) contentLength;
				useSubArray = false;
			} else {
				mainArraySize = chunkSize;
				subArraySize = (int) contentLength % chunkSize;
				useSubArray = true;
			}
			
			byte[] mainArray = new byte[mainArraySize];
			byte[] subArray = new byte[subArraySize];

			// Download to byte array
			byte b; // the current byte value
			boolean overwrite = true;
			
			for (int i = 0; i < contentLength; i++) {
				b = dataIn.readByte();
				mDownloadedSize++;
				
				if (i >= arrayCount * chunkSize && useSubArray) {
					subArray[i % chunkSize] = b;
				} else {
					mainArray[i % chunkSize] = b;
					if ((i + 1) % chunkSize == 0) {
						writeToFile(mainArray, mainArraySize, overwrite);
						overwrite = false;
					}
				}
			}
			
			// Write data to file
			if (useSubArray) 
				writeToFile(subArray, subArraySize, overwrite);
			*/

			// Read a chunk of given size at time and write the actual amount
			// of bytes read to the output file.
			byte[] dataArray = new byte[chunkSize];
			int result;
			
			// A boolean variable to determine whether to overwrite the output
			// file or not.
			// After the first time the writeToFile function is called, it will
			// be changed to false, which means the next times the data is 
			// written it is appended to the file.
			boolean overwrite = true;
			
			while (mDownloadedSize < contentLength) {
				result = dataIn.read(dataArray, 0, chunkSize);
				if (result == -1)
					break;
				
				mDownloadedSize += result;
				writeToFile(dataArray, result, overwrite);
				overwrite = false;
			}

			// Write info
			// TODO Remove comment
			System.out.println(mThread.getName() + " - downloaded size: " + mDownloadedSize);
		}
	}

	/**
	 * Write the given data to the download part file.
	 *
	 * @param bytes Byte array of data to write to the download part file.
	 * @param bytesToWrite Number of bytes in the byte array to be written.
	 * @param overwrite True if the file is to be overwritten by the given data.
	 * @throws IOException if failed to write to file.
	 */
	public void writeToFile(byte[] bytes, int bytesToWrite, boolean overwrite) throws IOException {
		try (FileOutputStream fout = new FileOutputStream(mFileName, !overwrite)) {
			// Write to the output file using FileChannel.
			FileChannel outChannel = fout.getChannel();
			
			// Wrap the given byte array in a ByteBuffer.
			ByteBuffer data = ByteBuffer.wrap(bytes, 0, bytesToWrite);
			
			// Write the data.
			outChannel.write(data);
		}
	}
	
	/**
	 * Gets the downloaded size.
	 * 
	 * @return The downloaded size.
	 */
	public long getDownloadedSize() {
		return mDownloadedSize;
	}
	
	/**
	 * Gets the size of the current part that needs to be downloaded.
	 * @return The size of the current part needed to be downloaded.
	 */
	public long getPartSize() {
		return mPartSize;
	}

	@Override
	public void run() {
		try {
			// Connect to the URL
			HttpURLConnection conn = getHttpConnection();

			// Download to file
			downloadToFile(conn);
		} catch (IOException ex) {
			Logger.getLogger(DownloadThread.class.getName()).log(Level.SEVERE, null, ex);
			throw new RuntimeException("Cannot connect to the given URL!");
		}
	}

}
