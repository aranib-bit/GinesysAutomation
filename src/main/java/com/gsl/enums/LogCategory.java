package com.gsl.enums;

/**
 * Categorizes a report log entry by functional domain.
 * Used with {@link ReportSeverity} to produce color-coded badge headers
 * in the ExtentReport, allowing readers to filter by area without scrolling
 * through mixed framework / business / DB noise.
 *
 * <p>Each constant also carries a debug/business classification (see {@link #isDebugCategory()}),
 * used to gate a category's visibility in the Extent HTML report behind
 * {@code report.debugLogs.extentVisible} (default {@code false} — business-only view) —
 * see {@code ExtentLogger#infoCategorized}/{@code #passCategorized}. This never affects file
 * logging via {@code ReportLogger#loggerFor}, which happens unconditionally regardless of this
 * flag.
 */
public enum LogCategory {
    BUSINESS(false),        // Business flow steps (voucher creation, form submission, navigation)
    UI_ACTION(true),        // Selenium interactions (click, sendKeys, wait)
    FIELD_ENTRY(true),      // Field value ENTRY/write (sendKeys/clear). Item AB (Round 5) reverses
                            // Round 3's original "always-visible" design, per explicit instruction —
                            // now debug-gated like UI_ACTION, hidden by default.
    FIELD_VALIDATION(true), // Field value read-back/VALIDATION after entry (getAttribute) — kept
                            // visually distinct from FIELD_ENTRY (Item P): a reader needs to tell
                            // "I just typed this" apart from "I just confirmed this is what the UI
                            // now shows" at a glance, not decode identical badges by message text.
                            // Item AB (Round 5): also debug-gated now, like UI_ACTION.
    VALIDATION(false),      // Assertions and checks (soft/hard assert, DB comparison)
    DB(false),              // Database queries and validation
    NETWORK(true),          // HTTP/API traffic
    JS(false),              // JavaScript console, browser errors
    PERF(true),             // Performance metrics and timing
    FRAMEWORK(false),       // Internal framework operations
    RCA(true),              // Root cause analysis hints
    SESSION(true),          // Browser/login lifecycle — startSession/login/logout/recoverSession
    NAVIGATION(false),      // Module/submodule navigation — real navigation vs. checkpoint-skip
    RECOVERY(true),         // RecoveryManager attempts (Levels 1-4) after a Navigation/Session/Browser failure
    MODULE(false),          // Top nav bar module click (Finance/Inventory/Procurement/Admin)
    SUB_MODULE(false),      // Collapsible sidebar group (e.g. Vouchers/Journals/Retail Journals/TDS)
    MENU(false),            // Top menu tab click/readback (e.g. Operations/Setup)
    SUB_MENU(false),        // Leaf side-menu item click (e.g. AP Voucher, AR Voucher)
    TEST_STEP_STATUS(false), // Step-level pass/fail outcome marker (StepExecutor)
    CONTEXT_SWITCH(true),   // Multi-context test surface switches (WEB/ANDROID/IOS/WEBAPI/PUBLICAPI) — ContextOrchestrator
    DATA_BACKFILL(false),   // WEBAPI/PUBLICAPI/k6 data-shortfall backfill — DataAvailabilityGuard
    VERIFICATION(false),    // Page/module-identity confirmation (VerifiablePage#verifyOnThisPage) — e.g.
                            // "General Voucher Add page opened", "Finance Module is loaded" — pass AND fail
    LOADER_WAIT(true),      // ExtJS loading-mask/splash-screen wait diagnostics (WaitUtils) — e.g. the
                            // "Loader wait [...] summary: N attempt(s)..." lines; low-level Selenium-wait
                            // infrastructure detail, not business content — a genuinely failed wait still
                            // escapes via its own FAIL/exception path, never gated by this
    POPUP(false);           // Dialog/popup handling (see PopupMonitor/DocumentMessageBox/
                            // PostSaveDialogHandler/LoginPage) — a single parent category; the
                            // EXPECTED/UNEXPECTED/MAYBE distinction is a separate sub-classification,
                            // not encoded here — see PopupOutcome and ExtentLogger#infoCategorized
                            // (LogCategory, PopupOutcome, String), which gates visibility on
                            // PopupOutcome#isDebugCategory() instead of this category's own flag

    private final boolean debugCategory;

    LogCategory(boolean debugCategory) {
        this.debugCategory = debugCategory;
    }

    /** {@code true} when this category is suppressed from the Extent HTML report by default
     *  (business-only view); {@code false} when it always shows. File logging is unaffected
     *  either way. */
    public boolean isDebugCategory() {
        return debugCategory;
    }
}
