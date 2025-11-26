package com.pandapf.util;

/**
 * Utility class for calculating SkyBlock Dungeon Catacombs Level from raw experience (XP).
 * This implementation uses the actual cumulative XP array for levels 1-50 and the
 * linear uncapped progression (200,000,000 XP/level) for level 51 and above.
 */
public class DungeonLevelUtil {

    // Cumulative XP required to start level N (Index N) for Catacombs Levels 1-50.
    // Index 0: 0 XP (Start Lvl 1)
    // Index 50: 453,559,640 XP (Start Lvl 50)
    // Index 51: 569,809,640 XP (Start Lvl 51, End of Capped progression)
    private static final long[] CATACOMBS_XP = {
            0L, 50L, 125L, 235L, 395L, 625L, 955L, 1425L, 2095L, 3045L, 4385L,
            6275L, 8940L, 12700L, 17960L, 25340L, 35640L, 50040L, 70040L, 97640L,
            135640L, 188140L, 259640L, 356640L, 488640L, 668640L, 911640L, 1239640L,
            1684640L, 2284640L, 3084640L, 4149640L, 5559640L, 7459640L, 9959640L,
            13259640L, 17559640L, 23159640L, 30359640L, 39559640L, 51559640L,
            66559640L, 85559640L, 109559640L, 139559640L, 177559640L, 225559640L,
            285559640L, 360559640L, 453559640L, // Index 50, XP to start Lvl 50.
            569809640L // Index 51, XP to start Lvl 51 (End of Capped progression)
    };

    // XP required per level past the capped level (Level 50)
    private static final double XP_PER_LEVEL_UNCAPPED = 200000000.0;

    // The total XP required to reach Level 51 (The end of capped progression and start of uncapped)
    // FIX: Updated from 360559640.0 to 569809640.0, which is CATACOMBS_XP[51].
    private static final double XP_TO_REACH_LEVEL_50 = 569809640.0;

    /**
     * Calculates the corresponding level (can be fractional) based on a given
     * amount of Catacombs experience (XP).
     *
     * @param xp The total Catacombs experience points accumulated (as a double).
     * @return The calculated level (e.g., 24.5 is level 24 with 50% progress).
     */
    public static double calculateLevel(double xp) {
        // Experience must be non-negative.
        if (xp <= 0) {
            return 1.0; // Level 1 starts at 0 XP
        }

        // --- 1. Capped Progression (Levels 1-50) ---
        // Uses array lookup and linear interpolation between two known points.
        if (xp < XP_TO_REACH_LEVEL_50) {

            // Iterate through the array to find the current level range
            for (int level = 1; level < CATACOMBS_XP.length; level++) {

                double xpToStartCurrentLevel = (double) CATACOMBS_XP[level];

                // If XP is less than the XP required to start the next level
                if (xp < xpToStartCurrentLevel) {

                    // The level achieved is level - 1
                    int achievedLevel = level - 1;

                    // The XP required to start the achieved level
                    double xpToStartAchievedLevel = (double) CATACOMBS_XP[achievedLevel];

                    // XP needed to complete the achieved level
                    double xpRequiredForCurrentLevel = xpToStartCurrentLevel - xpToStartAchievedLevel;

                    // XP accumulated towards the next level
                    double xpGainedThisLevel = xp - xpToStartAchievedLevel;

                    if (xpRequiredForCurrentLevel == 0) {
                        return (double) achievedLevel; // Should not happen often, but safe guard division by zero
                    }

                    // Fractional progress
                    double fractionalLevel = achievedLevel + (xpGainedThisLevel / xpRequiredForCurrentLevel);
                    return fractionalLevel;
                }
            }

            // If XP is >= the highest XP in the capped array (Index 51), treat it as Lvl 50.xx (should be handled by the check above)
            return 50.0;
        }

        // --- 2. Uncapped Progression (Levels 51+) ---
        // Uses the linear formula:
        // 50 + (xp - XP_TO_REACH_LEVEL_50) / XP_PER_LEVEL_UNCAPPED

        double excessXp = xp - XP_TO_REACH_LEVEL_50;
        double fractionalIncrease = excessXp / XP_PER_LEVEL_UNCAPPED;

        // Final level is 50 plus the fractional increase
        double level = 50.0 + fractionalIncrease;

        // Return level rounded to 2 decimal places
        return Math.floor(level * 100.0) / 100.0;
    }
}