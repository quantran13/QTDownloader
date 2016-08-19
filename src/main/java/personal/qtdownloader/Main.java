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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static String mURL;

    public static final String PROGRAM_DIR;
    public static final String DOWNLOADED_LIST_FILENAME;
    public static final String PROGRAM_TEMP_DIR;
    public static final HashMap<String, String> cmdLineOptions;
    public static HashMap<String, String> userOptions;

    /**
     * Initialize static final fields.
     */
    static {
        cmdLineOptions = new HashMap<>();
        cmdLineOptions.put("-o", "Output file's directory");
        cmdLineOptions.put("-f", "Output file name");
        cmdLineOptions.put("-h", "Print usage");
        cmdLineOptions.put("--help", "Print usage");
        cmdLineOptions.put("-u", "HTTP authorization username");
        cmdLineOptions.put("--username", "HTTP authorization username");
        cmdLineOptions.put("-p", "HTTP authorization password");
        cmdLineOptions.put("--password", "HTTP authorization password");
//        cmdLineOptions.put("--proxy-username", "Username for proxy");
//        cmdLineOptions.put("--proxy-password", "Password for proxy");

        String programDir = System.getenv("HOME") + "/.QTDownloader";
        String programTmpDir = programDir + "/tmp/";

        // Create the tmp folder if it doesn't exist
        File tmpDir = new File(programDir + "/tmp/");

        if (!tmpDir.exists()) {
            boolean mkdirSuccess = false;

            try {
                mkdirSuccess = tmpDir.mkdir();
            } catch (SecurityException se) {
                System.err.println(se.getMessage());
                System.exit(0);
            }

            if (!mkdirSuccess) {
                programTmpDir = programDir;
            }
        }

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            programDir = programDir.replace("/", "\\");
            programTmpDir = programTmpDir.replace("/", "\\");
        }

        PROGRAM_DIR = programDir;
        PROGRAM_TEMP_DIR = programTmpDir;
        DOWNLOADED_LIST_FILENAME = programDir + "/.filelist.csv";
    }

    /**
     *
     * @param args Array of arguments.
     * @throws java.lang.InterruptedException
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(0);
        }

        // Read the arguments
        userOptions = new HashMap<>();
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
        currentDownloadSession = checkIfFileWasDownloaded(downloadSessionList, 
                fileName, mURL);

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
        
        // Start new download with the given URL
        Download newDownload = new Download(mURL, partsCount);

        // Start the download.
        newDownload.startThread();

        // Wait for the main download thread to end.
        try {
            newDownload.joinThread();
        } catch (InterruptedException ex) {
            printErrorMessage(ex);
        }

        // Save the download to the downloaded file list
        currentDownloadSession.setDownloadSize(newDownload.getDownloadedSize());
        try {
            writeInfo(downloadSessionList);
        } catch (IOException ex) {
            printErrorMessage(ex);
        }

        // Print the current time
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Date date = new Date();
        System.out.println("Finished downloading!");
        System.out.println("\n--- " + dateFormat.format(date) + " ---");
    }

    /**
     * Read the options the user provided from the given list of arguments.
     *
     * @param args Array of arguments.
     */
    private static HashMap<String, String> readArgumentOptions(String[] args)
            throws RuntimeException {
        Set<String> validOptions = cmdLineOptions.keySet();
        HashMap<String, String> usrOptions = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (validOptions.contains(arg)) {
                String optionValue = null;
                try {
                    optionValue = args[i + 1];
                } catch (ArrayIndexOutOfBoundsException ex) {
                }

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

                        if (optionValue.charAt(optionValue.length() - 1) != '/') {
                            optionValue = optionValue + "/";
                        }

                        usrOptions.put("-o", optionValue);
                        i++;
                        break;
                    }
                    case "-f": {
                        /*
                         * -f: Output file name.
                         */

                        usrOptions.put("-f", optionValue);
                        i++;
                        break;
                    }
                    case "-u":
                    case "--username": {
                        /*
                         * -u or --username: Http username.
                         */

                        usrOptions.put("-u", optionValue);
                        i++;
                        break;
                    }
                    case "-p":
                    case "--password": {
                        /*
                         * -p or --password: Http password;
                         */

                        usrOptions.put("-p", optionValue);
                        i++;
                        break;
                    }
                    case "-h":
                    case "--help": {
                        /*
                         * Print the usage then exit.
                         */
                        printUsage();
                        System.exit(0);
                    }
                    default:
                        break;
                }
            } else {
                try {
                    URL url = new URL(arg);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("HEAD");
                    connection.connect();

                    if (i != args.length - 1) {
                        String errMessage = "qtdownloader: URL must be at the end!";
                        throw new RuntimeException(errMessage);
                    }
                } catch (IOException ex) {
                    String errMessage = "qtdownloader: Invalid option - \"" + arg + "\"";
                    throw new RuntimeException(errMessage);
                }
            }
        }

        return usrOptions;
    }

    /**
     * Print the usage.
     */
    private static void printUsage() {
        System.err.println("\nUsage: java -jar qtdownloader.jar [OPTIONS] URL");
        System.err.println("\nOptions: ");

        ArrayList<String> validOptions = new ArrayList<>(cmdLineOptions.keySet());
        Collections.sort(validOptions);

        validOptions.stream().forEach((String option) -> {
            System.err.println(String.format("\t%-20s: %s", option,
                    cmdLineOptions.get(option)));
        });

        System.err.println();
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
     * Read the list of downloaded files and URLs.
     *
     * @return A hashmap containing the information of the downloaded files, with the URL as key and an object of type DownloadSession as value.
     *
     * @throws IOException If failed to open the downloaded file list.
     */
    private static HashMap<String, DownloadSession> getListOfDownloadedFiles()
            throws IOException {
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
     * Check if the file being downloaded has been downloaded or not, or if the previous download attempt was interrupted.
     *
     * TODO find a better way to check if the file was downloaded, since http://test.com/test.bin and http://test.com/test.bin?i=1 are just the same.
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
    private static void writeInfo(HashMap<String, DownloadSession> sessionList)
            throws IOException {
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
