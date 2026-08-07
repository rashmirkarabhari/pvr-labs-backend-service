package com.pvrlabs.payment.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generates merchant order IDs in a predictable, human-readable format.
 * Format: PVR-ORD-yyyyMMdd-&lt;6 alphanumeric&gt;
 */
public final class OrderIdGenerator {

    private static final String PREFIX = "PVR-ORD";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private OrderIdGenerator() {
    }

    public static String generate() {
        return PREFIX + "-" + LocalDate.now().format(DATE) + "-" + randomSuffix(6);
    }

    private static String randomSuffix(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
