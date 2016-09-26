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
    private final long startPosition;
    private final long partSize;
    
    public JoinPartThread(String mainFileName, String partFileName, 
            long startPosition, long partSize) {
        this.mainFileName = mainFileName;
        this.partFileName = partFileName;
        this.startPosition = startPosition;
        this.partSize = partSize;
        
        System.out.println(startPosition);
    }
    
    private long writeDataToMainFile() throws IOException {
        try (RandomAccessFile mainFile = new RandomAccessFile(mainFileName, "rw");
                RandomAccessFile partFile = new RandomAccessFile(partFileName, "rw")) {
            FileChannel mainChannel = mainFile.getChannel();
            FileChannel partFileChannel = partFile.getChannel();
            
            // Try tranferring 10 times
            int trial = 0;
            long transferredBytes = 0;
            
            while (transferredBytes != partSize && trial < 10) {
                transferredBytes += mainChannel.transferFrom(partFileChannel,
                    startPosition + transferredBytes, partSize);
                trial++;
                System.out.println("Trying bitch");
            }
            
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
