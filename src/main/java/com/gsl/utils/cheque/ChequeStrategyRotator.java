package com.gsl.utils.cheque;

import com.gsl.utils.cheque.usa.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Selects which {@link ChequeNumberStrategy} implementation to use, either by an explicit
 * {@code cheque.country}-style config key ({@link #resolve(String)}) or by round-robin rotation
 * across all known strategies ({@link #next()}). This class only picks/instantiates a strategy —
 * {@link UniqueChequeGenerator} is the caller that actually invokes {@link ChequeNumberStrategy#generate()}
 * and owns the uniqueness retry loop.
 */
public class ChequeStrategyRotator {

    private static final List<ChequeNumberStrategy> STRATEGIES = List.of(
            new IndiaChequeStrategy(),
            new UsaChequeStrategy3(),
            new UsaChequeStrategy4(),
            new UsaChequeStrategy5(),
            new UsaChequeStrategy6(),
            new AlphaNumericChequeStrategy()
    );

    private static final AtomicInteger counter = new AtomicInteger(0);

    /** @return the next strategy in fixed round-robin order across all known strategies (wraps around). */
    public static ChequeNumberStrategy next() {
        // Mask sign bit before modulo to handle AtomicInteger overflow safely
        int idx = (counter.getAndIncrement() & Integer.MAX_VALUE) % STRATEGIES.size();
        return STRATEGIES.get(idx);
    }

    /**
     * Resolves a fresh strategy instance for a config-driven key (case-insensitive).
     *
     * @param key one of {@code INDIA}, {@code USA_3}..{@code USA_6}, {@code ALPHANUMERIC}, or
     *            {@code ROTATE} (delegates to {@link #next()})
     * @throws IllegalArgumentException if {@code key} doesn't match a known strategy
     */
    public static ChequeNumberStrategy resolve(String key) {
        return switch (key.toUpperCase()) {
            case "INDIA"        -> new IndiaChequeStrategy();
            case "USA_3"        -> new UsaChequeStrategy3();
            case "USA_4"        -> new UsaChequeStrategy4();
            case "USA_5"        -> new UsaChequeStrategy5();
            case "USA_6"        -> new UsaChequeStrategy6();
            case "ALPHANUMERIC" -> new AlphaNumericChequeStrategy();
            case "ROTATE"       -> next();
            default -> throw new IllegalArgumentException(
                    "Unknown cheque.country value: '" + key + "'. " +
                    "Valid values: INDIA, USA_3, USA_4, USA_5, USA_6, ALPHANUMERIC, ROTATE");
        };
    }
}
