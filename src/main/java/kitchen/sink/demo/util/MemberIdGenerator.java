package kitchen.sink.demo.util;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import java.security.SecureRandom;

/** Generated the former business member id. Kept only because the applied migration V004 still uses it. */
public final class MemberIdGenerator {

    public static final int LENGTH = 8;

    // 32 symbols, without look-alikes (0/O, 1/I)
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private MemberIdGenerator() {
    }

    /** Generates a random 8-character member id. */
    public static String generate() {
        return NanoIdUtils.randomNanoId(RANDOM, ALPHABET, LENGTH);
    }
}
