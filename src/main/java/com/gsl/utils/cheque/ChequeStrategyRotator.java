package com.gsl.utils.cheque;

import com.gsl.utils.cheque.usa.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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

    public static ChequeNumberStrategy next() {
        // Mask sign bit before modulo to handle AtomicInteger overflow safely
        int idx = (counter.getAndIncrement() & Integer.MAX_VALUE) % STRATEGIES.size();
        return STRATEGIES.get(idx);
    }

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
