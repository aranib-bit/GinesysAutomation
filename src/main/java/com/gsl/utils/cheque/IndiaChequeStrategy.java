package com.gsl.utils.cheque;

import java.security.SecureRandom;

public class IndiaChequeStrategy implements ChequeNumberStrategy {

    private static final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
