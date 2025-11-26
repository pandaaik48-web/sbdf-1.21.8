package com.pandapf.util;

public class TimeUtil {
    /**
     * Converts a time in milliseconds to a formatted string "Xm Ys".
     * @param timeInMs Time in milliseconds (or null if not available).
     * @return Formatted time string or "N/A".
     */
    public static String timeToString(Long timeInMs) {
        if (timeInMs == null || timeInMs <= 0) {
            return "N/A";
        }
        long totalSeconds = timeInMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
