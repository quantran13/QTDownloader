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

	public Progress() {
		mURLVerifyResult = new HttpResult(0, -1);
		ex = null;
		downloadFinish = false;
		joinPartsFinish = false;
	}
	
}
