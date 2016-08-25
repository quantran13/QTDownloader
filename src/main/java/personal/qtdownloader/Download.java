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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import static personal.qtdownloader.Main.mURL;

/**
 *
 * @author quan
 */
public class Download implements Runnable {

    protected final Progress progress;
    protected final boolean[] joinPartIsDone;
    
    private final String url;
    private final int partsCount;
    private final boolean mResume;
    private final String outputDirectory;
    private final String fileName;
    private final String originalFileName;
    private final String[] partNamesLists;
    private final HashMap<String, String> userOptions;
    private final Thread mThread;
    
    private final ExecutorService downloadThreadsPool;
    private final Future[] futurePool;
    
    private URL downloadUrl;

    /**
     * Constructor for the Download class which takes an URL string as parameter
     *
     * @param urlString
     * @param partsCount
     */
    public Download(String urlString, int partsCount) {
        this.url = urlString;
        this.downloadUrl = null;
        this.partsCount = partsCount;
        this.progress = new Progress();
        this.userOptions = Main.userOptions;
        this.downloadThreadsPool = Executors.newFixedThreadPool(partsCount);
        this.futurePool = new Future[partsCount];
        
        this.joinPartIsDone = new boolean[partsCount + 2];
        Arrays.fill(this.joinPartIsDone, false);
        
        // Get the user option for whether to resume downloading or not.
        this.mResume = "y".equals(userOptions.get("resume"));

        // Get the output directory, which is either specified by the user
        // or the current directory by default
        String outputDir = userOptions.get("-o");
        this.outputDirectory = (userOptions.containsKey("-o")) ? outputDir : "./";

        // Get the file name from either the user's option or the file's default name
        String usrFileName = userOptions.get("-f");
        this.fileName = (userOptions.containsKey("-f")) ? 
                usrFileName : (new File(url).getName());
        this.originalFileName = new File(url).getName();

        // Generate the list of part files' names.
        this.partNamesLists = new String[partsCount];
        for (int i = 0; i < partsCount; i++) {
            partNamesLists[i] = Main.PROGRAM_TEMP_DIR + "." + originalFileName
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

            if (userOptions.containsKey("-u") && userOptions.containsKey("-p")) {
                String username = userOptions.get("-u");
                String password = userOptions.get("-p");
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
    private ArrayList<DownloadThread> startDownloadThreads(int partCount) {
        ArrayList<DownloadThread> downloadThreadsList = new ArrayList<>(partCount);

        for (int i = 0; i < partCount; i++) {
            // Create new download threads and start them.
            DownloadThread downloadThread = new DownloadThread(i + 1, this);
            downloadThreadsList.add(downloadThread);
            
            Future result = downloadThreadsPool.submit(downloadThread);
            futurePool[i] = result;
        }

        return downloadThreadsList;
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
            System.err.println(ex.getMessage());
        }

        /*
         * Exit the program.
         */
        System.err.println("\nExiting!");
        System.exit(1);
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
    
    /**
     * Get the output file's path.
     * @return The output file's path.
     */
    public String getMainFilePath() {
        return outputDirectory + fileName;
    }
    
    /**
     * Get the size of the downloaded part.
     * 
     * @return The size of the downloaded part.
     */
    public long getDownloadedSize() {
        return progress.getDownloadedSize();
    }
    
    /**
     * Get the download URL.
     * 
     * @return The download URL.
     */
    public URL getDownloadURL() {
        return downloadUrl;
    }
    
    /**
     * Returns whether to resume the interrupted download or not.
     * 
     * @return Whether to resume the interrupted download or not.
     */
    public boolean resumeDownload() {
        return mResume;
    }
    
    /**
     * Get the number of parts to split the file into to download.
     * 
     * @return The number of parts.
     */
    public int getPartCount() {
        return partsCount;
    }
    
    public boolean downloadThreadsIsDone() {
        for (Future task : futurePool) {
            if (!task.isDone())
                return false;
        }
        
        return true;
    }

    /**
     * Start downloading from the given URL.
     */
    @Override
    public void run()   {
        // Start the download
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Date date = new Date();
        System.out.println("--- " + dateFormat.format(date) + " ---\n");
        System.out.println("Downloading from: " + mURL);
        
        // Create the URL object
        try {
            downloadUrl = new URL(url);
        } catch (MalformedURLException ex) {
            printErrorMessage(ex);
        }

        // Check the validity of the URL
        System.out.println("Sending HTTP request...");
        
        HttpResult result = null;
        try {
            result = checkURLValidity(downloadUrl);
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
        System.out.println();

        // Start the threads to download.
        ArrayList<DownloadThread> downloadParts;

        Instant start = Instant.now();
        progress.setStartDownloadTime(start);
        progress.setUrlVerifyResult(result);

        downloadParts = startDownloadThreads(partsCount);
        
        // Wait for the threads to finish downloading
        while (downloadThreadsIsDone()) {}
        
        for (Future futureTask : futurePool) {
            try {
                futureTask.get();
            }catch (ExecutionException ex) {
                Throwable exception = ex.getCause();
                printErrorMessage((Exception) exception);
            }catch (InterruptedException ex) {
                printErrorMessage(ex);
            }
        }
        
        downloadThreadsPool.shutdown();

        // Notify that all parts have finished downloading        
        Instant downloadFinish = Instant.now();
        double downloadTime = ((double) (Duration.between(start,
                downloadFinish).toMillis())) / 1000;
        System.out.println("\n\nTotal download time: " + downloadTime);
    }

}
