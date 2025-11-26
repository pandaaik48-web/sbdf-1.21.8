package com.pandapf.data;

import java.util.Map;

public class DungeonStats {
    public final Map<String, Map<Integer, PBData>> pb;
    public final double cataXp;
    public final int secrets;
    public final Map<String, Integer> runs;
    public final double cataLevel;

    /**
     * Inner class to hold Personal Best (PB) times for a single floor.
     */
    public static class PBData {
        public final String sTime; // Formatted "Xm Ys" for S rank
        public final String sPlusTime; // Formatted "Xm Ys" for S+ rank
        public final Long rawSTime;    // Raw time in milliseconds
        public final Long rawSPlusTime;

        public PBData(String sTime, String sPlusTime, Long rawSTime, Long rawSPlusTime) {
            this.sTime = sTime;
            this.sPlusTime = sPlusTime;
            this.rawSTime = rawSTime;
            this.rawSPlusTime = rawSPlusTime;
        }
    }

    public DungeonStats(Map<String, Map<Integer, PBData>> pb, double cataXp, int secrets, Map<String, Integer> runs, double cataLevel) {
        this.pb = pb;
        this.cataXp = cataXp;
        this.secrets = secrets;
        this.runs = runs;
        this.cataLevel = cataLevel;
    }
}