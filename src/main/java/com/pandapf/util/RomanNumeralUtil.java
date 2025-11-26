package com.pandapf.util;

import java.util.HashMap;
import java.util.Map;

public class RomanNumeralUtil {

    private static final Map<Character, Integer> ROMAN_MAP = new HashMap<>();

    static {
        ROMAN_MAP.put('I', 1);
        ROMAN_MAP.put('V', 5);
        ROMAN_MAP.put('X', 10);
        ROMAN_MAP.put('L', 50);
        ROMAN_MAP.put('C', 100);
        ROMAN_MAP.put('D', 500);
        ROMAN_MAP.put('M', 1000);
    }

    /**
     * Converts Roman numerals to integers.
     * Supports I-M (1-1000+).
     *
     * @param roman The Roman numeral string (e.g., "VII", "IX", "XLII")
     * @return The integer value, or 0 if invalid
     */
    public static int romanToInt(String roman) {
        if (roman == null || roman.isEmpty()) {
            return 0;
        }

        roman = roman.toUpperCase();
        int result = 0;
        int prevValue = 0;

        for (int i = roman.length() - 1; i >= 0; i--) {
            char ch = roman.charAt(i);
            int value = ROMAN_MAP.getOrDefault(ch, 0);

            if (value == 0) {
                // Invalid character
                return 0;
            }

            if (value < prevValue) {
                result -= value;
            } else {
                result += value;
            }
            prevValue = value;
        }

        return result;
    }

    /**
     * Converts integers to Roman numerals.
     * Supports 1-3999.
     *
     * @param num The integer to convert
     * @return The Roman numeral string, or empty string if out of range
     */
    public static String intToRoman(int num) {
        if (num <= 0 || num > 3999) {
            return "";
        }

        String[] thousands = {"", "M", "MM", "MMM"};
        String[] hundreds = {"", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM"};
        String[] tens = {"", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

        return thousands[num / 1000] +
                hundreds[(num % 1000) / 100] +
                tens[(num % 100) / 10] +
                ones[num % 10];
    }
}