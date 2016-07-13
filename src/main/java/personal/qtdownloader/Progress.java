/**
 * Class: Progress.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

import java.time.Duration;
import java.time.Instant;

public class Progress {

    public HttpResult mURLVerifyResult;
    public Exception ex;
    public boolean downloadFinished;
    public boolean joinPartsFinished;

    public long downloadedCount;
    public long time;
    public long sizeChange;
    public long percentageCount;
    
    public Instant startDownloadTimeStamp;

    public Progress() {
        mURLVerifyResult = new HttpResult(0, -1);
        ex = null;
        downloadFinished = false;
        joinPartsFinished = false;
        downloadedCount = 0;
        time = 0;
        sizeChange = 0;
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
