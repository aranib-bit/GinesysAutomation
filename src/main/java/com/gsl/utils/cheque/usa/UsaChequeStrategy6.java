package com.gsl.utils.cheque.usa;

import com.gsl.utils.cheque.ChequeNumberStrategy;

import java.security.SecureRandom;

public class UsaChequeStrategy6 implements ChequeNumberStrategy {

    private static final int LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
