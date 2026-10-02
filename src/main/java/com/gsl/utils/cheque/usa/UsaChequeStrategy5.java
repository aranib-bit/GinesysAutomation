package com.gsl.utils.cheque.usa;

import com.gsl.utils.cheque.ChequeNumberStrategy;

import java.security.SecureRandom;

/**
 * {@link ChequeNumberStrategy} for a US cheque number: a plain 5-digit numeric string
 * (e.g. {@code "04291"}). One of four fixed-length siblings — {@link UsaChequeStrategy3},
 * {@link UsaChequeStrategy4}, {@link UsaChequeStrategy6} — that differ only in digit count,
 * modeling the range of serial lengths seen on real US cheque stock. Selected via
 * {@link com.gsl.utils.cheque.ChequeStrategyRotator#resolve(String)} with key {@code "USA_5"}.
 */
public class UsaChequeStrategy5 implements ChequeNumberStrategy {

    private static final int LENGTH = 5;
    private static final SecureRandom random = new SecureRandom();

    /** @return a {@value #LENGTH}-digit numeric string; not guaranteed unique. */
    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
