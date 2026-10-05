package com.gsl.utils.dataprovider.source.finance.common;

/**
 * Which kind of document scheme {@link DocSchemeResolver} should hand back for a voucher menu.
 */
public enum DocSchemeMode {

    /**
     * Default. An auto-numbered ({@code manual='N'}), non-test scheme that still has numbers left;
     * the one with the most remaining wins. Fails fast if none is available.
     */
    AUTO,

    /** An enabled manual-numbering ({@code manual='Y'}) scheme. No capacity check — the user types the number. */
    MANUAL,

    /**
     * A scheme with no numbers left, for the "Save on a full scheme must show a proper error"
     * negative regression. Test-named schemes are preferred. If none is full the caller gets a
     * {@code SkipException} (never a silent pass).
     */
    EXHAUSTED
}
