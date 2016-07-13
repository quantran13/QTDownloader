/**
 * Class: DownloadSession.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

/**
 * A class containing the information about a particular download session.
 * 
 * @author quan
 */
public class DownloadSession {

    private final String mFileName;
    private final String mURL;
    private long mDownloadedSize;
    public boolean alreadyDownloaded;
    public boolean resumeDownload;
    public boolean cancelDownload;

    DownloadSession(String fileName, String url, long downloadedSize) {
        mFileName = fileName;
        mURL = url;
        mDownloadedSize = downloadedSize;
    }

    public String getFileName() {
        return mFileName;
    }

    public String getURL() {
        return mURL;
    }

    public long getDownloadedSize() {
        return mDownloadedSize;
    }

    public void setDownloadSize(long downloadSize) {
        mDownloadedSize = downloadSize;
    }

}
