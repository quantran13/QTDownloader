/**
 * Class: Main.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;

public class Main {

    public static String mURL;
    public static HashMap<String, String> userOptions;
    public static Connection dbConn;

    public static final String PROGRAM_DIR;
    public static final String PROGRAM_TEMP_DIR;
    public static final String DATABASE_FILE;
    public static final String DATABASE_PATH;
    public static final String TABLE_NAME;
    
    public static final HashMap<String, String> cmdLineOptions;

    /**
     * Initialize static final fields.
     */
    static {
        // Add command line options
        cmdLineOptions = new HashMap<>();
        cmdLineOptions.put("-o", "Output file's directory");
        cmdLineOptions.put("-f", "Output file name");
        cmdLineOptions.put("-h", "Print usage");
        cmdLineOptions.put("--help", "Print usage");
        cmdLineOptions.put("-u", "HTTP authorization username");
        cmdLineOptions.put("--username", "HTTP authorization username");
        cmdLineOptions.put("-p", "HTTP authorization password");
        cmdLineOptions.put("--password", "HTTP authorization password");

        // Set up necessary directory paths
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
        
        // Set up database path
        DATABASE_FILE = "qtdb";
        DATABASE_PATH = "jdbc:h2:" + programDir + "\\" + DATABASE_FILE;
        TABLE_NAME = "sessions";
    }

    /**
     *
     * @param args Array of arguments.
     */
    public static void main(String[] args) {
        // Parse the arguments
        if (args.length == 0) {
            printUsage();
            System.exit(0);
        }

        userOptions = new HashMap<>();
        try {
            userOptions = readArgumentOptions(args);
        } catch (RuntimeException ex) {
            printErrorMessage(ex);
        }

        mURL = args[args.length - 1]; // The url is the last argument.
        int partsCount = 8;           // Number of parts to divide to download.
        
        // Set up the database
        setUpDatabase();
        
        // Check if the file has been downloaded or not
        String fileName = new File(mURL).getName();
        DownloadSession currentDownloadSession;
        currentDownloadSession = checkIfFileWasDownloaded(fileName, mURL);

        // If the last attempt to download the file was interrupted and
        // the user chose to resume downloading.
        userOptions.put("resume", currentDownloadSession.resumeDownload ? "y" : "n");

        // If the user chooses to cancel downloading, exit the program
        if (currentDownloadSession.cancelDownload) {
            return;
        }

        System.out.print("\n");
        
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
        writeDownloadSessionInfoToDB(currentDownloadSession);

        // Print the current time
        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        Date date = new Date();
        System.out.println("Finished downloading!");
        System.out.println("\n--- " + dateFormat.format(date) + " ---");
        
        // Close the database
        try {
            dbConn.close();
        } catch (SQLException ex) {
            // Fuck it then
        }
    }
    
    /**
     * Connect to the database and set up the table for using.
     */
    public static void setUpDatabase() {
        try {
	    Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            // If the class cannot be found (which should never happen)
            // The program cannot save the download information in the database
            // TODO Log the error
            
            System.out.println("[WARNING] Cannot find h2 driver: org.h2.driver ");
            return;
        }
        
        try {
            dbConn = DriverManager.getConnection(DATABASE_PATH, "", "");
        } catch (SQLException ex) {
            System.out.println("[WARNING] Cannot connect to the database");
            return;
        }
        
        // Create the table if not exists
        String createTableQuery = "CREATE TABLE IF NOT EXISTS "
                + TABLE_NAME + " ("
                + "ID INT NOT NULL AUTO_INCREMENT,"
                + "PRIMARY KEY(ID),"
                + "FileName VARCHAR(255),"
                + "DownloadURL VARCHAR(2083),"
                + "DownloadedSize BIGINT,"
                + ");";
        
        try {
            PreparedStatement stmt = dbConn.prepareStatement(createTableQuery);
            stmt.executeUpdate();
        } catch (SQLException ex) {
            System.out.println("[WARNING] Cannot create information table in the database");
        }
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
     * Check if the file being downloaded has been downloaded or not, 
     * or if the previous download attempt was interrupted.
     *
     * TODO find a better way to check if the file was downloaded, since http://test.com/test.bin and http://test.com/test.bin?i=1 are just the same.
     *
     * @param downloadSessionList List of downloaded files and URLs.
     */
    private static DownloadSession checkIfFileWasDownloaded(String fileName, String url) {
        DownloadSession session = new DownloadSession(fileName, url, -1);
        session.alreadyDownloaded = false;
        session.resumeDownload = false;
        
        // If the database connection is null then assume that the download is new
        if (dbConn == null)
            return session;
        
        try {
            String selectDownloadQuery = "SELECT * FROM " + TABLE_NAME
                    + " WHERE DownloadURL='" + url + "';";
            PreparedStatement stmt = dbConn.prepareStatement(selectDownloadQuery);
            ResultSet result = stmt.executeQuery();
            
            if (result.next()) {
                long downloadedSize = result.getLong("DownloadedSize");
                session.alreadyDownloaded = true;
                
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
                        session.resumeDownload = true;
                    }
                } else {
                    // If it's not -1, the last download attempt succeeded.
                    System.out.print("\nYou downloaded from this URL. "
                            + "Do you want to download again? (y/n) ");

                    char answer = 0;
                    Scanner reader = new Scanner(System.in);
                    while (answer != 'y' && answer != 'Y'
                            && answer != 'n' && answer != 'N') {
                        answer = reader.next().charAt(0);
                    }

                    if (answer == 'n' || answer == 'N') {
                        session.cancelDownload = true;
                    }
                }
            }
        } catch (SQLException ex) {
            // Cannot select the data from database
            // So we consider this download session as being new
            // TODO Log the error.
        }

        return session;
    }

    /**
     * Write the information for the current download session to the database.
     * 
     * @param session The current download session
     */
    private static void writeDownloadSessionInfoToDB(DownloadSession session) {
        // If the database connection is null then don't write information to
        // the database
        
        if (dbConn == null)
            return;
        
        // Get the download information
        long downloadedSize = session.getDownloadedSize();
        String url = session.getURL();
        String fileName = session.getFileName();
        
        try {
            if (session.alreadyDownloaded) {
                // If the file has been downloaded before,
                // update the downloaded size.
                String updateInfoQuery = "UPDATE " + TABLE_NAME
                        + " SET DownloadedSize=" + downloadedSize
                        + " WHERE DownloadURL=" + url + ";";
                PreparedStatement stmt = dbConn.prepareStatement(updateInfoQuery);
                stmt.executeUpdate();
            } else {
                // If the file hasn't been downloaded before
                // insert a new entry into the table
                String insertInfoQuery = "INSERT INTO " + TABLE_NAME
                        + " (DownloadURL, DownloadedSize, FileName) "
                        + " VALUES ('" + url + "', "
                        + downloadedSize + ", '"
                        + fileName + "');";
                PreparedStatement stmt = dbConn.prepareStatement(insertInfoQuery);
                stmt.executeUpdate();
            }
        } catch (SQLException ex) {
            // IF failed to write the info, the information will not be written.
            // Next time the file is downloaded from the url, it will be treated
            // as a new download.
            // TODO Log the error.
        }
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
        ex.printStackTrace();
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
}
