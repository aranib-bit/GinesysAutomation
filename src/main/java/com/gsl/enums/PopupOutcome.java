package com.gsl.enums;

/**
 * Sub-classification of a {@code LogCategory.POPUP}-categorized report entry — a secondary badge
 * dimension, mirroring how {@link ReportSeverity} already renders alongside a category badge
 * (see {@code ExtentLogger#buildCategorizedHtml}). Deliberately NOT folded into
 * {@link LogCategory} itself as separate flat constants (e.g. {@code POPUP_EXPECTED}/
 * {@code POPUP_UNEXPECTED}) — the user explicitly asked for one parent {@code POPUP} category
 * with EXPECTED/UNEXPECTED/MAYBE as its own sub-category, not three joined-together categories.
 *
 * <ul>
 *   <li>{@link #EXPECTED} — a dialog/popup the flow is designed to interact with, handled as part
 *       of the normal, intended path (e.g. a Confirm dialog dismissed by {@code PostSaveDialogHandler}).</li>
 *   <li>{@link #UNEXPECTED} — a genuine edge-case/error dialog outside the normal flow (e.g. an
 *       {@code UnexpectedPopupException} case in {@code PopupMonitor}).</li>
 *   <li>{@link #MAYBE} — a dialog that conditionally appears depending on prior state, neither
 *       reliably present nor a hard anomaly — e.g. {@code LoginPage#handleConcurrentSessionPopup}'s
 *       "already logged in from another browser" prompt, which only shows up depending on whatever
 *       session state happened to exist before this login attempt.</li>
 * </ul>
 *
 * <p>Each constant carries its own debug/business visibility classification (mirroring
 * {@link LogCategory#isDebugCategory()}'s per-constant design) — gated by
 * {@code ExtentLogger#infoCategorized(LogCategory, PopupOutcome, String)}, which checks THIS
 * enum's flag instead of the parent {@code LogCategory.POPUP}'s own (which carries no useful
 * signal on its own once every popup outcome shares one parent category).
 */
public enum PopupOutcome {
    /** Hidden by default — routine, flow-designed dialog handling. */
    EXPECTED(true),
    /** Always shown — a genuine edge-case/error dialog a reader should see. */
    UNEXPECTED(false),
    /** Always shown — a conditional/contextual dialog outcome worth surfacing, even though it
     *  isn't a hard anomaly. */
    MAYBE(false);

    private final boolean debugCategory;

    PopupOutcome(boolean debugCategory) {
        this.debugCategory = debugCategory;
    }

    /** {@code true} when this outcome is suppressed from the Extent HTML report by default
     *  (business-only view); {@code false} when it always shows. File logging is unaffected
     *  either way. */
    public boolean isDebugCategory() {
        return debugCategory;
    }
}
