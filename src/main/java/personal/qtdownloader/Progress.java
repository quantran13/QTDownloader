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

    private HttpResult mURLVerifyResult;
    private long downloadedCount;
    private long downloadedSinceStart;
    private Instant startDownloadTimeStamp;

    public Progress() {
        mURLVerifyResult = new HttpResult(0, -1);
        downloadedCount = 0;
        downloadedSinceStart = 0;
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
    
    public void updateDownloadedSinceStart(long sizeChange) {
        downloadedSinceStart += sizeChange;
    }
    
    public void setUrlVerifyResult(HttpResult result) {
        mURLVerifyResult = result;
    }
    
    public void setStartDownloadTime(Instant start) {
        startDownloadTimeStamp = start;
    }

    public void updateProgressBar() {
        // Get the percentage of the part downloaded
        long contentSize = mURLVerifyResult.contentLength;
        double percent = ((double) downloadedCount / (double) contentSize) * 100;
        percent = (double) ((int)Math.round(percent * 100)) / 100;

        // Calculate speed
        Instant now = Instant.now();
        long timeElapsed = Duration.between(startDownloadTimeStamp, now).toMillis();
        double timeInSeconds = (double)timeElapsed / 1000;
        
        double speed = 0;
        if (timeInSeconds != 0)
            speed = (double) ((double)downloadedSinceStart / timeInSeconds);
        
        // Convert speed to the readable format
        String[] speedUnits = {"bytes", "KB", "MB", "GB", "TB", "PB", "EB"};

        int unit = 0;
        while (speed > 1024 && unit < speedUnits.length) {
            speed = speed / 1024;
            unit++;
        }

        speed = (double) Math.round(speed * 100) / 100;

        // Create the progress bar based on the current percentage
        String done = new String(new char[(int) percent]).replace("\0", "#");
        String undone = new String(new char[100 - (int) percent]).replace("\0", " ");
        
        // Print the progress bar
//        System.out.format("\r[" + done + undone + "] %6.2f%% %7.2f"
//                + speedUnits[unit] + "/s    ", percent, speed);
    }
}
