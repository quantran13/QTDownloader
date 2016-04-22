/**
 * Class: DownloadSession.java
 *
 * @author quan
 *
 */

package personal.downloadmanager;

/**
 *
 * @author quan
 */
public class DownloadSession {
	
	private final String mFileName;
	private final String mURL;
	private long mDownloadedSize;
	
	DownloadSession(String fileName, String url, long downloadedSize) {
		mFileName = fileName;
		mURL = url;
		mDownloadedSize = downloadedSize;
	}
	
	public String getFileName()	{
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
