/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 * Class: HttpResult.java
 *
 * @author quan
 *
 */

package personal.quantran.downloadmanager;

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
