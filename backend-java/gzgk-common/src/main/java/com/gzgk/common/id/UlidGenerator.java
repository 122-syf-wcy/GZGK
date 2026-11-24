package com.gzgk.common.id;

import java.security.SecureRandom;

/**
 * Lexicographically sortable 26-char Crockford Base32 ULID generator.
 * Allows gzgk-core to generate globally unique planId/eventId synchronously in memory
 * before async Kafka persistence without waiting on a database sequence round-trip.
 */
public final class UlidGenerator {
    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {
    }

    public static String nextUlid() {
        long time = System.currentTimeMillis();
        char[] out = new char[26];
        // 48-bit timestamp -> first 10 chars
        for (int i = 9; i >= 0; i--) {
            out[i] = ENCODING[(int) (time & 31L)];
            time >>>= 5;
        }
        // 80-bit cryptographically strong randomness -> last 16 chars
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);
        long hi = ((rand[0] & 0xFFL) << 32) | ((rand[1] & 0xFFL) << 24)
                | ((rand[2] & 0xFFL) << 16) | ((rand[3] & 0xFFL) << 8) | (rand[4] & 0xFFL);
        long lo = ((rand[5] & 0xFFL) << 32) | ((rand[6] & 0xFFL) << 24)
                | ((rand[7] & 0xFFL) << 16) | ((rand[8] & 0xFFL) << 8) | (rand[9] & 0xFFL);
        for (int i = 17; i >= 10; i--) {
            out[i] = ENCODING[(int) (hi & 31L)];
            hi >>>= 5;
        }
        for (int i = 25; i >= 18; i--) {
            out[i] = ENCODING[(int) (lo & 31L)];
            lo >>>= 5;
        }
        return new String(out);
    }
}
