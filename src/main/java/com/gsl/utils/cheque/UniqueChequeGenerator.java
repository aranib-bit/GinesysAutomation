package com.gsl.utils.cheque;

import com.gsl.config.ConfigFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public entry point for cheque number generation: resolves the configured {@link
 * ChequeNumberStrategy} (via {@link ChequeStrategyRotator#resolve(String)} and the
 * {@code cheque.country} finance config), then retries generation until a number not yet
 * handed out by this JVM is produced. Backed by a process-wide {@link ConcurrentHashMap}-based
 * set, so uniqueness is enforced across all threads/tests sharing this classloader — not
 * against the database.
 */
public class UniqueChequeGenerator {

    private static final Set<String> generated = ConcurrentHashMap.newKeySet();

    /** @return a cheque number in the configured strategy's format, guaranteed not previously returned by this class since the last {@link #reset()}. */
    public static String generateUnique() {
        String key = ConfigFactory.getFinanceConfig().chequeCountry();
        ChequeNumberStrategy strategy = ChequeStrategyRotator.resolve(key);
        String cheque;
        do {
            cheque = strategy.generate();
        } while (!generated.add(cheque)); // atomic check-and-add, retries on collision
        return cheque;
    }

    /** Clears the seen-cheque set, allowing previously generated numbers to be reissued. */
    public static void reset() {
        generated.clear();
    }
}
