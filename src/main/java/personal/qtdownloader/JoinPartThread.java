/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package personal.qtdownloader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.concurrent.Callable;

/**
 *
 * @author Quan
 */
public class JoinPartThread implements Callable<Long> {
    
    private final String mainFileName;
    private final String partFileName;
    private final long partSize;
    
    public JoinPartThread(String mainFileName, String partFileName, long partSize) {
        this.mainFileName = mainFileName;
        this.partFileName = partFileName;
        this.partSize = partSize;
    }
    
    private long writeDataToMainFile() throws IOException {
        try (RandomAccessFile mainFile = new RandomAccessFile(mainFileName, "rw");
                RandomAccessFile partFile = new RandomAccessFile(partFileName, "rw")) {
            FileChannel mainChannel = mainFile.getChannel();
            FileChannel partFileChannel = partFile.getChannel();
            
            // Start appending the data at the end of the main file
            long mainFileSize = mainFile.length();
            System.out.print("\nStart of " + partFileName + " " + mainFileSize);
            
            // Try tranferring until it's done or an exception is thrown
            long transferredBytes = 0;
            
            while (transferredBytes != partSize) {
                transferredBytes += mainChannel.transferFrom(partFileChannel,
                    mainFileSize + transferredBytes, partSize);
            }
            
            System.out.print("\nYay done " +  partFileName + " " + transferredBytes);
            System.out.print("\nSize after transfer: " + mainFile.length());
            
            return transferredBytes;
        }
    }
    
    /**
     *
     * @return
     * @throws Exception
     */
    @Override
    public Long call() throws Exception {
        Long result = writeDataToMainFile();
        
        return result;
    }
    
}
