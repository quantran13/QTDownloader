/**
 * Class: Download.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 *
 * @author quan
 */
public class DownloadThread implements Callable<Long> {

    //private Thread mThread;
    private long startByte;
    private long endByte;
    private long partSize;
    private final boolean resume;
    private URL url;
    private long downloadedSize;
    private long alreadyDownloadedSize;

    private final long startCopyPosition;
    private final int partNumber;
    private final String mFileName;
    private final Download currentDownload;
    private final HashMap<String, String> userOptions;

    /**
     * Construct a download object with the given URL and byte range to downloads
     *
     * @param partNumber The part of the file being downloaded.
     * @param download The main download thread.
     */
    public DownloadThread(int partNumber, Download download) {
        this.partNumber = partNumber;
        
        // Calculate the start byte and end byte
        partSize = download.progress.getContentSize() / download.getPartCount();
        
        long start_byte = (partNumber - 1) * partSize;
        long end_byte;
        if (partNumber == download.getPartCount())
            end_byte = download.progress.getContentSize() - 1;
        else
            end_byte = partNumber * partSize - 1;
        
        this.startByte = start_byte;
        this.endByte = end_byte;
        this.startCopyPosition = startByte;
        this.partSize = end_byte - start_byte + 1;
        this.resume = download.resumeDownload();
        this.url = download.getDownloadURL();
        downloadedSize = 0;
        alreadyDownloadedSize = 0;
        userOptions = Main.userOptions;

        // Get the file name.
        mFileName = Main.PROGRAM_TEMP_DIR + "."
                + (new File(url.toExternalForm()).getName()
                + ".part" + partNumber);

        currentDownload = download;

        // If resume a download then set the start byte
        if (resume) {
            try (RandomAccessFile partFile = new RandomAccessFile(mFileName, "rw")) {
                alreadyDownloadedSize = partFile.length();
                this.startByte += alreadyDownloadedSize;
                downloadedSize += alreadyDownloadedSize;
            } catch (IOException ex) {
                // If cannot open the part file, leave the start byte as it is
                // to download the entire part again.
            }
        }
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
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        String downloadRange = "bytes=" + startByte + "-" + endByte;
        conn.setRequestProperty("Range", downloadRange);

        // Get the http login credentials and set the corresponding properties
        // in the http connection varible
        if (userOptions.containsKey("-u") && userOptions.containsKey("-p")) {
            String username = userOptions.get("-u");
            String password = userOptions.get("-p");
            String credentials = username + ":" + password;
            credentials = Base64.getEncoder().encodeToString(credentials.getBytes());

            conn.setRequestProperty("Authorization", "Basic " + credentials);
        }

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
        int chunkSize = (int) Math.pow(2, 13); // 8KB

        try (DataInputStream dataStream = new DataInputStream(is)) {
            // Get the file's length.
            long contentLength = conn.getContentLengthLong();
            contentLength += alreadyDownloadedSize;

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
            if (resume) {
                overwrite = false;
            }

            synchronized (currentDownload.progress) {
                currentDownload.progress.updateDownloadedSize(downloadedSize);
                currentDownload.progress.updateProgressBar();
                currentDownload.progress.notifyAll();
            }

            // While the total downloaded size is still smaller than the 
            // content length from the connection, keep reading data.
            while (downloadedSize < contentLength) {
                result = dataStream.read(dataArray, 0, chunkSize);

                if (result == -1) {
                    break;
                }

                downloadedSize += result;
                writeToFile(dataArray, result, overwrite);
                overwrite = false;

                synchronized (currentDownload.progress) {
                    currentDownload.progress.updateDownloadedSize(result);
                    currentDownload.progress.updateDownloadedSinceStart(result);
                    //currentDownload.progress.updateProgressBar();

                    currentDownload.progress.notifyAll();
                }
            }
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
    public void writeToFile(byte[] bytes, int bytesToWrite, boolean overwrite) 
            throws IOException {
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
        return downloadedSize;
    }

    /**
     * Gets the size of the current part that needs to be downloaded.
     *
     * @return The size of the current part needed to be downloaded.
     */ 
    public long getPartSize() {
        return partSize;
    }

    @Override
    public Long call() throws Exception {
        // Connect to the URL
        HttpURLConnection conn = getHttpConnection();

        // Download to file
        downloadToFile(conn);
        
        // Check if the download was incomplete or not
        if (downloadedSize != partSize) {
            String errMessage = "Download incomplete at part " + partNumber + "!";
            throw new RuntimeException(errMessage);
        }
        
        // Delete the main file if it exists
        Path mainFilePath = Paths.get(currentDownload.getMainFilePath());
        try {
            Files.deleteIfExists(mainFilePath);
        } catch (IOException ex) {
            // TODO Log the error
        }
        
        // Write the data to the main file from the part file
        synchronized (currentDownload.joinPartIsDone)  {
            if (partNumber != 1) 
                while (!currentDownload.joinPartIsDone[partNumber - 2])
                    currentDownload.joinPartIsDone.wait();
        }
        
        ExecutorService joinPartsThreadPool = Executors.newFixedThreadPool(1);
        JoinPartThread joinPartThrd = new JoinPartThread(
                currentDownload.getMainFilePath(), 
                mFileName, startCopyPosition, partSize);
        
        // Get the result and compare to the size of the part the thread is 
        // downloading.
        Future<Long> result = joinPartsThreadPool.submit(joinPartThrd);
        Long transferredBytes;
        try {
            transferredBytes = result.get();
            System.out.println("Yay done " + partNumber + " " + transferredBytes);
        } catch (ExecutionException ex) {
            String errMessage = "Error while transferring from part " +
                    partNumber + " to the main file!";
            throw new RuntimeException(errMessage, ex.getCause());
        } catch (SecurityException ex) {
            String errMessage = "You do not have the permission to the output"
                    + "file!";
            throw new RuntimeException(errMessage, ex);
        }
        
        joinPartsThreadPool.shutdown();
        synchronized (currentDownload.joinPartIsDone) {
            currentDownload.joinPartIsDone[partNumber - 1] = true;
            currentDownload.joinPartIsDone.notifyAll();
        }
        
        if (transferredBytes != partSize) {
            String errMessage = "Transfer from part file to main file incomplete"
                    + " at part " + partNumber + "!";
            errMessage += " " + transferredBytes + " " + partSize;
            throw new RuntimeException(errMessage);
        }
        
        return downloadedSize;
    }

}
