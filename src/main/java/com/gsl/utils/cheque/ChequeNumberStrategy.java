package com.gsl.utils.cheque;

/**
 * Strategy contract for generating a single cheque number in one country/format's shape.
 * Implementations hold no shared state beyond their own RNG and never decide uniqueness
 * themselves — {@link UniqueChequeGenerator} owns the retry-on-collision loop, and
 * {@link ChequeStrategyRotator} owns picking/instantiating which strategy runs.
 *
 * <p>Current implementations:
 * <ul>
 *   <li>{@link IndiaChequeStrategy}</li>
 *   <li>{@link com.gsl.utils.cheque.usa.UsaChequeStrategy3}</li>
 *   <li>{@link com.gsl.utils.cheque.usa.UsaChequeStrategy4}</li>
 *   <li>{@link com.gsl.utils.cheque.usa.UsaChequeStrategy5}</li>
 *   <li>{@link com.gsl.utils.cheque.usa.UsaChequeStrategy6}</li>
 *   <li>{@link AlphaNumericChequeStrategy}</li>
 * </ul>
 */
public interface ChequeNumberStrategy {
    /** @return a freshly generated cheque number in this strategy's own format; not guaranteed unique. */
    String generate();
}
