package com.gsl.browser;

/**
 * Names the three browser-log pass/fail policies — STRICT/SMART/AUDIT — that
 * {@link DecisionEngine#evaluate} implements directly off config flags.
 * TODO-confirm: no code currently references this enum itself (grepped, no hits outside this
 * file) — it may be a planned config-driven replacement for {@link DecisionEngine}'s flag checks,
 * or dead code left over from an earlier version of that logic.
 */
public enum BrowserLogMode {
    /** Any critical JS error fails the test. */
    STRICT,
    /** Only critical JS errors on an already-failing test cause a failure. */
    SMART,
    /** Capture logs but never influence pass/fail. */
    AUDIT
}
