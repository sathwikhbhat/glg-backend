package com.golinkgone.glgbackend.service;

import java.security.SecureRandom;

public class ShortKeyGenerator {
    private static final SecureRandom random = new SecureRandom();

    private ShortKeyGenerator() {
    }

    public static String generateShortKey() {
        StringBuilder shortKey = new StringBuilder(6);
        final String CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 6; i++) {
            shortKey.append(CHAR_SET.charAt(random.nextInt(CHAR_SET.length())));
        }
        return shortKey.toString();
    }
}
