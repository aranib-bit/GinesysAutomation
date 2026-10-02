package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Which page-footer buttons a voucher form shows, by form mode and document status:
 * <ul>
 *   <li>{@link #ADD} — Copy, Save and Close.</li>
 *   <li>{@link #EDIT_UNPOSTED} — Save and Close (Copy is Add-only).</li>
 *   <li>{@link #POSTED} — Close only (Save is shown only for an Unposted document).</li>
 * </ul>
 * Drives {@code VoucherFooterBase#validateFooterButtonsUi}: an expectation of {@code false} is
 * checked as "absent by design".
 */
public enum FooterMode {
    ADD(true, true, true),
    EDIT_UNPOSTED(false, true, true),
    POSTED(false, false, true);

    private final boolean copy;
    private final boolean save;
    private final boolean close;

    FooterMode(boolean copy, boolean save, boolean close) {
        this.copy = copy;
        this.save = save;
        this.close = close;
    }

    public boolean copyExpected()  { return copy; }
    public boolean saveExpected()  { return save; }
    public boolean closeExpected() { return close; }

    /** Default mapping for callers that only know add vs edit (an Edit form is Unposted unless told otherwise). */
    public static FooterMode of(boolean isEditMode) {
        return isEditMode ? EDIT_UNPOSTED : ADD;
    }
}
