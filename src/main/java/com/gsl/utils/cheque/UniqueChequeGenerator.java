package com.gsl.utils.cheque;

import com.gsl.config.ConfigFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UniqueChequeGenerator {

    private static final Set<String> generated = ConcurrentHashMap.newKeySet();

    public static String generateUnique() {
        String key = ConfigFactory.getConfig().chequeCountry();
        ChequeNumberStrategy strategy = ChequeStrategyRotator.resolve(key);
        String cheque;
        do {
            cheque = strategy.generate();
        } while (!generated.add(cheque)); // atomic check-and-add, retries on collision
        return cheque;
    }

    public static void reset() {
        generated.clear();
    }
}
