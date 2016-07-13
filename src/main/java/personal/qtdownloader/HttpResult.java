/**
 * Class: HttpResult.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

public class HttpResult {

    public int responseCode;
    public long contentLength;

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
