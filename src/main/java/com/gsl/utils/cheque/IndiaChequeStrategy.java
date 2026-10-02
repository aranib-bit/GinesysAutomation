package com.gsl.utils.cheque;

import java.security.SecureRandom;

/**
 * {@link ChequeNumberStrategy} for India-format cheque numbers: a plain 6-digit numeric
 * string (e.g. {@code "042917"}), matching the standard Indian MICR cheque leaf length.
 * Selected via {@link ChequeStrategyRotator#resolve(String)} with key {@code "INDIA"}.
 */
public class IndiaChequeStrategy implements ChequeNumberStrategy {

    private static final SecureRandom random = new SecureRandom();

    /** @return a 6-digit numeric string, e.g. {@code "042917"}; not guaranteed unique. */
    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
