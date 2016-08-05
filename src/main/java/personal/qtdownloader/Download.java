/**
 * Class: DownloadDemo.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import static personal.qtdownloader.Main.mURL;

/**
 *
 * @author quan
 */
public class Download implements Runnable {

    private final String mUrl;
    private final int mPartsCount;
    protected final Progress mProgress;
    private final boolean mResume;
    private final String mOutputDirectory;
    private final String mFileName;
    private final String mOriginalFileName;
    private final String[] mPartNameLists;
    private final HashMap<String, String> mUserOptions;

    private final Thread mThread;

    /**
     * Constructor for the Download class which takes an URL string as parameter
     *
     * @param urlString
     * @param partsCount
     * @param progress
     */
    public Download(String urlString, int partsCount) {
        mUrl = urlString;
        mPartsCount = partsCount;
        mProgress = new Progress();
        mUserOptions = Main.userOptions;
        
        // Get the user option for whether to resume downloading or not.
        mResume = "y".equals(mUserOptions.get("resume"));

        // Get the output directory, which is either specified by the user
        // or the current directory by default
        String outputDir = mUserOptions.get("-o");
        mOutputDirectory = (mUserOptions.containsKey("-o")) ? outputDir : "./";

        // Get the file name from either the user's option or the file's default name
        String fileName = mUserOptions.get("-f");
        mFileName = (mUserOptions.containsKey("-f")) ? fileName : (new File(mUrl).getName());
        mOriginalFileName = new File(mUrl).getName();

        // Generate the list of part files' names.
        mPartNameLists = new String[partsCount];
        for (int i = 0; i < partsCount; i++) {
            mPartNameLists[i] = Main.PROGRAM_TEMP_DIR + "." + mOriginalFileName
                    + ".part" + (i + 1);
        }

        // Create a thread for this main download thread.
        mThread = new Thread(this, "Main download thread");
    }

    /**
     * Check the validity of the given URL.
     *
     * @param urlString The given URL.
     * @return The content size from the requested URL. If -1 then the response from the server is not success.
     *
     * @throws ConnectException if failed to connect to the given URL.
     */
    private HttpResult checkURLValidity(URL url) throws ConnectException {
        // Create new connection from the given url
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Connect to the created connection.
            conn.setRequestMethod("HEAD");

            if (mUserOptions.containsKey("-u") && mUserOptions.containsKey("-p")) {
                String username = mUserOptions.get("-u");
                String password = mUserOptions.get("-p");
                String credentials = username + ":" + password;
                credentials = Base64.getEncoder().encodeToString(credentials.getBytes());

                conn.setRequestProperty("Authorization", "Basic " + credentials);
            }

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
     * @param progress
     *
     * @return An ArrayList of downloading thread objects.
     */
    public ArrayList<DownloadThread> startDownloadThreads(URL url, long contentSize,
            int partCount, Progress progress) {
        long partSize = contentSize / partCount;
        ArrayList<DownloadThread> downloadThreadsList = new ArrayList<>(partCount);

        for (int i = 0; i < partCount; i++) {
            // Calculate the begin and end byte for each part.
            long beginByte = i * partSize;
            long endByte;
            if (i == partCount - 1) {
                endByte = contentSize - 1;
            } else {
                endByte = (i + 1) * partSize - 1;
            }

            long currentPartSize = endByte - beginByte + 1;

            // Create new download threads and start them.
            DownloadThread downloadThread = new DownloadThread(url, beginByte,
                    endByte, currentPartSize, i + 1, this, mResume);
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
    public void joinDownloadedParts(String fileName, ArrayList<DownloadThread> downloadParts)
            throws IOException {
        String outputFile = mOutputDirectory + fileName;

        try (RandomAccessFile mainFile = new RandomAccessFile(outputFile, "rw")) {
            FileChannel mainChannel = mainFile.getChannel();
            long startPosition = 0;

            for (int i = 0; i < downloadParts.size(); i++) {
                String partName = mPartNameLists[i];

                try (RandomAccessFile partFile = new RandomAccessFile(partName, "rw")) {
                    long partSize = downloadParts.get(i).getDownloadedSize();
                    FileChannel partFileChannel = partFile.getChannel();
                    long transferedBytes = mainChannel.transferFrom(partFileChannel,
                            startPosition, partSize);

                    startPosition += transferedBytes;

                    if (transferedBytes != partSize) {
                        String errMessage = "Error joining file at part: "
                                + (i + 1) + "!\n";
                        errMessage += "Transfered bytes: " + transferedBytes;
                        throw new RuntimeException(errMessage);
                    }
                }
            }
        }
    }

    /**
     * Start the thread.
     */
    public void startThread() {
        mThread.start();
    }

    /**
     * Join the thread.
     *
     * @throws InterruptedException if the thread is interrupted.
     */
    public void joinThread() throws InterruptedException {
        mThread.join();
    }
    
    public long getDownloadedSize() {
        return mProgress.getDownloadedSize();
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
            System.err.println("\nFailed to connect to the given URL: "
                    + connectException.getMessage());
            System.err.println("\nCheck your internet connection or URL again.");
        } else if (ex instanceof IOException) {
            System.err.println("\nFailed to open the output file: "
                    + ex.getMessage());
        } else if (ex instanceof InterruptedException) {
            System.err.println("\nOne of the thread was interrupted: "
                    + ex.getMessage());
        } else if (ex instanceof RuntimeException) {
            System.err.println(Arrays.toString(ex.getStackTrace()));
            System.err.println(ex.getMessage());
        }

        /*
         * Exit the program.
         */
        System.err.println("\nExiting!");
        System.exit(1);
    }

    /**
     * Start downloading from the given URL.
     */
    @Override
    public void run() {
        // Start the download
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Date date = new Date();
        System.out.println("--- " + dateFormat.format(date) + " ---\n");
        System.out.println("Downloading from: " + mURL);
        
        // Get the file name and create the URL object
        String fileName = mFileName;
        URL url = null;
        
        try {
            url = new URL(mUrl);
        } catch (MalformedURLException ex) {
            printErrorMessage(ex);
        }

        // Check the validity of the URL
        System.out.println("Sending HTTP request...");
        
        HttpResult result = null;
        try {
            result = checkURLValidity(url);
        } catch (ConnectException ex) {
            printErrorMessage(ex);
        }
        
        long contentSize = result.contentLength;
        int responseCode = result.responseCode;

        if (contentSize == -1 || responseCode != 200) {
            String errMessage = "Error while checking URL validity!";
            errMessage += "\nResponse code: " + responseCode;
            errMessage += "\nContent size: " + contentSize;
            printErrorMessage(new RuntimeException(errMessage));
        }
        
        System.out.println("Response code: " + result.responseCode);
        System.out.println("Fize size: "
                + Utility.readableFileSize(result.contentLength));

        // Start the threads to download.
        ArrayList<DownloadThread> downloadParts;

        mProgress.startDownloadTimeStamp = Instant.now();
        Instant start = Instant.now();

        downloadParts = startDownloadThreads(url, contentSize,
                    mPartsCount, mProgress);

        // Wait for the threads to finish downloading
        for (int i = 0; i < downloadParts.size(); i++) {
            DownloadThread currentThread = downloadParts.get(i);
            
            try {
                currentThread.joinThread();
            } catch (InterruptedException ex) {
                printErrorMessage(ex);
            }
            
            if (currentThread.getDownloadedSize() != currentThread.getPartSize()) {
                String errMessage = "Download incompleted at part "
                        + (i + 1) + ": " + currentThread.getDownloadedSize();
                
                printErrorMessage(new RuntimeException(errMessage));
            }
        }

        // Notify that all parts have finished downloading        
        Instant downloadFinish = Instant.now();
        double downloadTime = ((double) (Duration.between(start,
                downloadFinish).toMillis())) / 1000;
        System.out.println("\n\nTotal download time: " + downloadTime);

        try {
            // Join the downloaded parts
            joinDownloadedParts(fileName, downloadParts);
        } catch (IOException ex) {
            printErrorMessage(ex);
        }

        // Delete part files
        try {
            for (int i = 0; i < downloadParts.size(); i++) {
                String partName = mPartNameLists[i];
                Path filePath = Paths.get(partName);
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ex) {
            // If failed to delete then just ignore the exception.
            // What can we do?
            // TODO Log the error
        }
        
        Instant joinFinishedTime = Instant.now();
        double joinTime = ((double) (Duration.between(downloadFinish,
                joinFinishedTime).toMillis())) / 1000;

        System.out.println("Total join time: " + joinTime);
    }

}
