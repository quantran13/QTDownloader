/**
 * Class: HttpResult.java
 *
 * @author quan
 *
 */

package personal.downloadmanager;

public class HttpResult {
	public final int responseCode;
	public final long contentLength;
	
	/**
	 *
	 * @param r The response code.
	 * @param c The content length.
	 */
	public HttpResult(int r, long c) {
		responseCode = r;
		contentLength = c;
	}
}
