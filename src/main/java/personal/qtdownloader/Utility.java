/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Class: Utility.java
 *
 * @author quan
 *
 */
package personal.qtdownloader;

public class Utility {

    /**
     * Converts the given amount of bytes to human readable size in KB, MB, ...
     *
     * @param bytes Amount of bytes to convert.
     * @return A string containing the size in readable form.
     */
    public static String readableFileSize(double bytes) {
        String[] fileSizeUnits = {"bytes", "KB", "MB", "GB", "TB", "PB", "EB"};
        String result = "";

        double size = bytes;
        int unit = 0;
        while (size > 1024 && unit < fileSizeUnits.length) {
            size = size / 1024;
            unit++;
        }

        size = (double) Math.round(size * 100) / 100;
        result += String.valueOf(size) + " " + fileSizeUnits[unit];

        return result;
    }

}
