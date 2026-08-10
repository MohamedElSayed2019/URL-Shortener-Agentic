package com.hcb.sc.load.shortener.service;

/**
 * Encodes non-negative long identifiers into short, URL-safe Base62 strings.
 * Chosen over random strings so collisions are structurally impossible for
 * unique input IDs (e.g., an atomic counter or sequence).
 */
public final class Base62Encoder {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();
    // Reverse lookup table for O(1) character-to-value decoding instead of a
    // linear ALPHABET.indexOf(c) scan (O(62)) per character.
    private static final int[] DECODE_TABLE = new int[128];
    static {
        java.util.Arrays.fill(DECODE_TABLE, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            DECODE_TABLE[ALPHABET.charAt(i)] = i;
        }
    }

    private Base62Encoder() {}

    public static String encode(long value) {
        if (value == 0) return String.valueOf(ALPHABET.charAt(0));
        if (value < 0) throw new IllegalArgumentException("value must be non-negative");
        StringBuilder sb = new StringBuilder();
        long v = value;
        while (v > 0) {
            int rem = (int) (v % BASE);
            sb.append(ALPHABET.charAt(rem));
            v /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long result = 0;
        int len = code.length();
        for (int i = 0; i < len; i++) {
            char c = code.charAt(i);
            int idx = c < DECODE_TABLE.length ? DECODE_TABLE[c] : -1;
            if (idx < 0) throw new IllegalArgumentException("Invalid base62 character: " + c);
            result = result * BASE + idx;
        }
        return result;
    }
}
