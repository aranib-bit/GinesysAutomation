package com.gsl.utils.cheque;

import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link ChequeNumberStrategy} that generates alphanumeric cheque numbers using four
 * real-world patterns, selected with equal probability per call for variety distribution.
 * Selected via {@link ChequeStrategyRotator#resolve(String)} with key {@code "ALPHANUMERIC"}.
 *
 * Patterns:
 *   BANK_BRANCH      – 3 alpha (bank code) + 3 digits (branch)  e.g. SBI001
 *   COUNTRY_SERIAL   – 2 alpha (country code) + 4 digits        e.g. IN4731
 *   LONG_ALPHA_SERIAL– 3 alpha + 5 digits (8 chars total)       e.g. ABC12345
 *   RANDOM_MIXED     – 6–10 fully random alphanumeric chars      e.g. F4K9QR7P
 */
public class AlphaNumericChequeStrategy implements ChequeNumberStrategy {

    private static final String ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String CHARS = ALPHA + DIGITS;
    private static final SecureRandom random = new SecureRandom();

    /** @return a cheque number in one of the four patterns above, chosen at random; not guaranteed unique. */
    @Override
    public String generate() {
        int pattern = ThreadLocalRandom.current().nextInt(4);
        return switch (pattern) {
            case 0 -> bankBranch();
            case 1 -> countrySerial();
            case 2 -> longAlphaSerial();
            default -> randomMixed();
        };
    }

    /** e.g. SBI001 — 3 alpha bank code + 3 digit branch */
    private String bankBranch() {
        return randomAlpha(3) + randomDigits(3);
    }

    /** e.g. IN4731 — 2 alpha country code + 4 digit serial */
    private String countrySerial() {
        return randomAlpha(2) + randomDigits(4);
    }

    /** e.g. ABC12345 — 3 alpha + 5 digits */
    private String longAlphaSerial() {
        return randomAlpha(3) + randomDigits(5);
    }

    /** e.g. F4K9QR7P — 6 to 10 random alphanumeric chars */
    private String randomMixed() {
        int length = 6 + random.nextInt(5);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String randomAlpha(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(ALPHA.charAt(random.nextInt(ALPHA.length())));
        return sb.toString();
    }

    private String randomDigits(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        return sb.toString();
    }
}
