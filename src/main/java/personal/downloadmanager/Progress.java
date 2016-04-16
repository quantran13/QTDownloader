/**
 * Class: Progress.java
 *
 * @author quan
 *
 */
package personal.downloadmanager;

public class Progress {
	
	public HttpResult mURLVerifyResult;
	public Exception ex;
	public boolean downloadFinish;
	public boolean joinPartsFinish;
	
	public long downloadedCount;
	
	public Progress() {
		mURLVerifyResult = new HttpResult(0, -1);
		ex = null;
		downloadFinish = false;
		joinPartsFinish = false;
		downloadedCount = 0;
	}
	
	public void updateProgressBar() {
		long contentSize = mURLVerifyResult.contentLength;
		double percent = ((double)downloadedCount / (double)contentSize) * 100;
		percent = (double) Math.round(percent * 100) / 100;
		
		String done = new String(new char[(int)percent]).replace("\0", "#");
		String undone = new String(new char[100 - (int)percent]).replace("\0", " ");
		System.out.print("\r[" + done + undone + "] " + String.valueOf(percent) 
			+ "%");
	}
}
