package com.gsl.browser;

public enum BrowserLogMode {
    /** Any critical JS error fails the test. */
    STRICT,
    /** Only critical JS errors on an already-failing test cause a failure. */
    SMART,
    /** Capture logs but never influence pass/fail. */
    AUDIT
}
