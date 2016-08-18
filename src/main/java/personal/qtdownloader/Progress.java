/**
 * Class: Progress.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

import java.time.Instant;

public class Progress {

    private HttpResult mURLVerifyResult;
    private Exception ex;
    private long downloadedCount;
    private long sizeChange;
    private Instant startDownloadTimeStamp;

    public Progress() {
        mURLVerifyResult = new HttpResult(0, -1);
        ex = null;
        downloadedCount = 0;
        sizeChange = 0;
    }
    
    public long getContentSize() {
        return mURLVerifyResult.contentLength;
    }
    
    public long getDownloadedSize() {
        return downloadedCount;
    }
    
    public void updateDownloadedSize(long downloadedSize) {
        downloadedCount += downloadedSize;
    }
    
    public void setUrlVerifyResult(HttpResult result) {
        mURLVerifyResult = result;
    }
    
    public void setException(Exception ex) {
        this.ex = ex;
    }
    
    public void setStartDownloadTime(Instant start) {
        startDownloadTimeStamp = start;
    }
    
    public void setSizeChange(long sizeChange) {
        this.sizeChange = sizeChange;
    }

    public void updateProgressBar() {
        // Get the percentage of the part downloaded
        long contentSize = mURLVerifyResult.contentLength;
        double percent = ((double) downloadedCount / (double) contentSize) * 100;
        percent = (double) ((int)Math.round(percent * 100)) / 100;

        // TODO find a way to calculate the speed and display it properly.
        // Calculate speed
//        Instant now = Instant.now();
//        long timeElapsed = Duration.between(now, startDownloadTimeStamp).getNano();
//        double time = (double)timeElapsed;

        double speed = 0;
//        if (time != 0)
//            speed = (double) ((double)downloadedCount * (time / 1000000000));
        String speedString = Utility.readableFileSize(speed) + "/s";

        String done = new String(new char[(int) percent]).replace("\0", "#");
        String undone = new String(new char[100 - (int) percent]).replace("\0", " ");
        System.out.print("\r[" + done + undone + "] " + String.valueOf(percent)
                + "% " + speedString);
    }
}
