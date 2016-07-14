/**
 * Class: Main.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.nio.file.InvalidPathException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static String mURL;

    private static final String PROGRAM_DIR = System.getenv("HOME")
            + "/.QTDownloader";
    private static final String DOWNLOADED_LIST_FILENAME = PROGRAM_DIR
            + "/.filelist.csv";

    /**
     *
     * @param args Array of arguments.
     * @throws java.lang.InterruptedException
     */
    public static void main(String[] args) throws InterruptedException {
        // Read the arguments
        HashMap<String, String> userOptions;
        try {
            userOptions = readArgumentOptions(args);
        } catch (RuntimeException ex) {
            printErrorMessage(ex);
        }
        
        mURL = args[args.length - 1]; // The url is the last argument.
        int partsCount = 8;           // Number of parts to divide to download.

        // Get the list of downloaded files
        HashMap<String, DownloadSession> downloadSessionList = null;
        try {
            downloadSessionList = getListOfDownloadedFiles();
        } catch (IOException ex) {
            printErrorMessage(ex);
        }

        // Check if the file has been downloaded or not
        String fileName = new File(mURL).getName();
        DownloadSession currentDownloadSession;
        currentDownloadSession = checkIfFileWasDownloaded(downloadSessionList, fileName, mURL);

        // If the file was downloaded before.
        boolean downloaded = currentDownloadSession.alreadyDownloaded;
        
        // If the last attempt to download the file was interrupted and
        // the user chose to resume downloading.
        userOptions.put("resume", currentDownloadSession.resumeDownload ? "y" : "n");

        // If the user chooses to cancel downloading, exit the program
        if (currentDownloadSession.cancelDownload) {
            return;
        }

        System.out.print("\n");

        // If failed to read from the download list file
        // create a new hashmap for the download sessions list
        if (downloadSessionList == null) {
            downloadSessionList = new HashMap<>();
        }

        if (downloaded) {
            currentDownloadSession = downloadSessionList.get(mURL);
            currentDownloadSession.setDownloadSize(-1);
        } else {
            currentDownloadSession = new DownloadSession(fileName, mURL, -1);
            downloadSessionList.put(mURL, currentDownloadSession);
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

        Download newDownload = new Download(mURL, partsCount, progress, userOptions);

        // Start the download.
        Instant start = Instant.now();
        newDownload.startThread();

        // Verify URL
        System.out.println("Sending HTTP request...");
        synchronized (progress) {
            // Wait until verification of the URL succeeds or an exception is thrown.
            while (progress.mURLVerifyResult.responseCode == 0
                    && progress.ex == null) {
                progress.wait();
            }

            if (progress.ex == null) {
                // If no exception was thrown, URL verification succeeds.
                System.out.println("Response code: "
                        + progress.mURLVerifyResult.responseCode);
                System.out.println("Fize size: "
                        + Utility.readableFileSize(progress.mURLVerifyResult.contentLength));
            } else {
                // Else print the error message and exit.
                printErrorMessage(progress.ex);
            }
        }

        System.out.println();

        // Wait for the download to finish
        Instant downloadFinish = null;

        synchronized (progress) {
            // Wait until the download finishes or an exception is thrown.
            while (!progress.downloadFinished && progress.ex == null) {
                progress.wait();
            }

            if (progress.ex == null) {
                // If no exception was thrown. the file was downloaded successfully.
                downloadFinish = Instant.now();
                double downloadTime = ((double) (Duration.between(start,
                        downloadFinish).toMillis())) / 1000;

                System.out.println("\n\nTotal download time: " + downloadTime);
            } else {
                // Else print the error message and exit.
                printErrorMessage(progress.ex);
            }
        }

        // Wait for the parts to finish joining.
        Instant joinFinishedTime;

        synchronized (progress) {
            // Wait until all parts finish joining or an exception is thrown.
            while (!progress.joinPartsFinished && progress.ex == null) {
                progress.wait();
            }

            if (progress.ex == null) {
                // If no exception is thrown, parts joining succeeds.
                joinFinishedTime = Instant.now();
                double joinTime = ((double) (Duration.between(downloadFinish,
                        joinFinishedTime).toMillis())) / 1000;

                System.out.println("Total join time: " + joinTime);
            } else {
                // Else print the error message and exit.
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
        currentDownloadSession.setDownloadSize(progress.downloadedCount);
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
     * Read the options the user provided from the given list of arguments.
     * 
     * @param args Array of arguments.
     */
    private static HashMap<String, String> readArgumentOptions(String[] args) throws RuntimeException {
        ArrayList<String> validOptions = new ArrayList<>(Arrays.asList("-o"));
        HashMap<String, String> userOptions = new HashMap<>();

        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];
            
            if (validOptions.contains(arg)) {
                String optionValue = args[++i];

                switch (arg) {
                    case "-o": {
                        /*
                         * -o: Output folder to save the downloaded file to.
                         */
                        
                        // Verify the validity of the output folder path.
                        try {
                            File filePath = new File(optionValue);

                            if (!filePath.isDirectory()) {
                                String errMessage = "qtdownloader: "
                                        + "Invalid output file path - "
                                        + optionValue;
                                throw new RuntimeException(errMessage);
                            }
                        } catch (InvalidPathException ex) {
                            String errMessage = "qtdownloader: Invalid output file path - "
                                    + optionValue;
                            throw new RuntimeException(errMessage);
                        }
        
                        userOptions.put("-o", optionValue);
                    }
                }
            } else {
                String errMessage = "qtdownloader: Invalid option - \"" + arg + "\"";
                throw new RuntimeException(errMessage);
            }
        }
        
        return userOptions;
    }

    /**
     * Print the usage.
     */
//    private static void printUsage() {
//        System.err.println("Usage: java -jar qtdownloader.jar <url>\n");
//    }

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
            System.err.println("\n" + ex.getMessage());
        }

        /*
         * Exit the program.
         */
        System.err.println("\nExiting!");
        System.exit(0);
    }

    /**
     * Read the list of downloaded files and URLs.
     *
     * @return A hashmap containing the information of the downloaded files, with the URL as key and an object of type DownloadSession as value.
     *
     * @throws IOException If failed to open the downloaded file list.
     */
    private static HashMap<String, DownloadSession> getListOfDownloadedFiles() throws IOException {
        // The download files list is store in a hashmap, with the url being the key.
        HashMap<String, DownloadSession> downloadedList = new HashMap<>();

        try (FileReader downloadedFileList = new FileReader(DOWNLOADED_LIST_FILENAME);
                BufferedReader br = new BufferedReader(downloadedFileList)) {
            String line;

            try {
                while ((line = br.readLine()) != null) {
                    String[] wordList = line.split(",");

                    if (wordList.length == 3) {
                        // If the entry has 3 parts, the first is the download url,
                        // the second is the file name, and the third is the 
                        // file size. Also, this would mean that the download 
                        // was completed.
                        String fileName = wordList[0];
                        String downloadUrl = wordList[1];
                        long downloadedSize = Long.parseLong(wordList[2]);

                        DownloadSession ds = new DownloadSession(fileName,
                                downloadUrl, downloadedSize);

                        downloadedList.put(downloadUrl, ds);
                    } else if (wordList.length == 2) {
                        // If the entry has 2 parts, the first is the download url,
                        // the second is the file name.
                        // Also, this would mean that the download was incomplete.
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
     * Check if the file being downloaded has been downloaded or not, 
     * or if the previous download attempt was interrupted.
     * 
     * TODO find a better way to check if the file was downloaded,
     * since http://test.com/test.bin and http://test.com/test.bin?i=1 are just the same.
     *
     * @param downloadSessionList List of downloaded files and URLs.
     */
    private static DownloadSession checkIfFileWasDownloaded(HashMap<String, DownloadSession> downloadSessionList, 
            String fileName, String url) {
        DownloadSession downloadSession = new DownloadSession(fileName, url, -1);
        downloadSession.alreadyDownloaded = false;
        downloadSession.resumeDownload = false;

        for (Map.Entry<String, DownloadSession> entry : downloadSessionList.entrySet()) {
            String currentUrl = entry.getKey();

            if (currentUrl.equals(mURL)) {
                // If the given URL match one of the URLs in the downloaded files list.
                downloadSession.alreadyDownloaded = true;

                DownloadSession ds = entry.getValue();
                long downloadedSize = ds.getDownloadedSize();

                if (downloadedSize == -1) {
                    // Downloaded size equal -1 means that the last 
                    // download attempt failed.
                    System.out.print("\nYour previous attempt to download"
                            + " from this URL was interrupted. ");
                    System.out.print("Do you want to resume downloading? "
                            + "(y/n) ");

                    char answer = 0;
                    Scanner reader = new Scanner(System.in);
                    while (answer != 'y' && answer != 'Y'
                            && answer != 'n' && answer != 'N') {
                        answer = reader.next().charAt(0);
                    }

                    if (answer == 'y' || answer == 'Y') {
                        downloadSession.resumeDownload = true;
                    }

                    break;
                } else {
                    System.out.print("\nYou downloaded from this URL. "
                            + "Do you want to download again? (y/n) ");

                    char answer = 0;
                    Scanner reader = new Scanner(System.in);
                    while (answer != 'y' && answer != 'Y'
                            && answer != 'n' && answer != 'N') {
                        answer = reader.next().charAt(0);
                    }

                    if (answer == 'n' || answer == 'N') {
                        downloadSession.cancelDownload = true;
                    }
                }

                break;
            }
        }

        return downloadSession;
    }

    /**
     * Write the list of downloaded files and URLs to file.
     *
     * @param sessionList List of downloaded files and URLs.
     * @throws IOException
     */
    private static void writeInfo(HashMap<String, DownloadSession> sessionList) throws IOException {
        try (FileWriter tmpFile = new FileWriter(DOWNLOADED_LIST_FILENAME, false);
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
