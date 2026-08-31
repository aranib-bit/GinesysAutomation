package com.gsl.enums.Finance.Operations.Common;

import com.gsl.enums.PopupType;

public enum DocumentMessageBoxMessage {

    /** Appears when On Account is toggled to Yes — "OK, Go Ahead" button clears sub-ledger/TDS/payment rows. */
    SUBLEDGER_CLEAR_ON_ACCOUNT_CHANGE(
            "All Sub ledger, adjustments, TDS and payment details will get cleared. Do you want to proceed?",
            PopupType.WARNING
    ),

    /**
     * Appears when Deduct TDS is changed from Yes → No in the sub-ledger grid,
     * <b>but only when Amount (Dr) already has a value</b> — set directly or
     * auto-reflected after clicking "Calc. Amount".
     *
     * <p>Trigger sequence:
     * <ol>
     *   <li>Sub-ledger row has Deduct TDS = Yes.</li>
     *   <li>Amount (Dr) is populated (manually, or via Calc. Amount &rarr; OK).</li>
     *   <li>User changes Deduct TDS Yes &rarr; No &rarr; this popup appears.</li>
     * </ol>
     *
     * <p>The popup does <em>not</em> appear when Amount (Dr) is blank, or when Deduct TDS
     * is changed Yes &rarr; Yes, No &rarr; No, or No &rarr; Yes.
     *
     * <p>Buttons: "Ok, Go Ahead" (proceed, TDS/Payable cleared) | "Close" (cancel, reverts).
     */
    TDS_PAYABLE_CLEAR_CONFIRMATION(
            "Calculated TDS and Payable amount will get cleared. Do you want to proceed?",
            PopupType.CONFIRM
    ),

    /** Appears on save when no instrument number is supplied — "Yes"/"No" or "Ignore & Proceed". */
    MISSING_INSTRUMENT_NUMBER(
            "Do you want to proceed without instrument number?",
            PopupType.CONFIRM
    ),

    /** Appears after save when TDS Provided = Yes and auto-release was not performed — "Continue"/"No" buttons. */
    AUTO_TDS_JOURNAL_PROMPT(
            "Auto TDS Journal will be created for this voucher. Do you want to continue?",
            PopupType.CONFIRM
    ),

    /** Appears when user clicks the Amount cell after a document has been adjusted — single OK button. */
    DOC_AMOUNT_READ_ONLY_INFO(
            "Document is adjusted against the selected sub ledger, amount can not be modified manually.",
            PopupType.INFO
    ),

    /** Appears after clicking Revert on a currently Posted document — Yes/No buttons; Yes un-posts it. */
    REVERT_UNPOST_CONFIRMATION(
            "Selected document(s) will be un-posted. Do you want to proceed?",
            PopupType.CONFIRM
    ),

    /**
     * Appears after clicking Revert on a document already in un-posted state — single OK button,
     * no state change. Live text is {@code "Document No: <docno> - Document is already in
     * un-posted state."}; this constant deliberately omits the dynamic "Document No: X - "
     * prefix so {@link com.gsl.pages.Common.DocumentMessageBox#verifyMessage} /
     * {@code contains()}-based checks still match regardless of which document triggered it.
     */
    REVERT_ALREADY_UNPOSTED_INFO(
            "Document is already in un-posted state.",
            PopupType.INFO
    ),

    /**
     * Appears after clicking Revert on a document whose {@code finvchdet} line(s) are already
     * bank-reconciled (BRS) — single OK button, no state change; Revert is blocked. Live text is
     * {@code "Document No: <docno> - BRS is done for this transaction. Cannot be reverted."};
     * this constant deliberately omits the dynamic "Document No: X - " prefix, same convention
     * as {@link #REVERT_ALREADY_UNPOSTED_INFO}.
     */
    REVERT_BRS_ALREADY_DONE(
            "BRS is done for this transaction. Cannot be reverted.",
            PopupType.INFO
    ),

    /**
     * Appears after clicking Revert on a document whose own accounting year or financial month
     * is already closed ({@code admou_year.admyear_status='C'} or
     * {@code admou_month.fin_month_status='C'} — see {@code AccountingPeriodResolver}) — single
     * OK button, no state change; Revert is blocked. Live text
     * <b>CONFIRMED</b> from a live test run's captured report (run {@code 20260831_140246},
     * {@code FIN-GV-REVERT-05} through {@code 09}):
     * {@code "Document No: <docno> - Finance month is already closed. Cannot be reverted."};
     * this constant deliberately omits the dynamic "Document No: X - " prefix, same convention
     * as {@link #REVERT_ALREADY_UNPOSTED_INFO}/{@link #REVERT_BRS_ALREADY_DONE}.
     */
    REVERT_MONTH_ALREADY_CLOSED(
            "Finance month is already closed. Cannot be reverted.",
            PopupType.INFO
    ),

    /**
     * Appears after confirming Yes on {@link #REVERT_UNPOST_CONFIRMATION} — a separate
     * "Result" dialog, single OK button, confirming the un-post actually completed. Live text
     * is {@code "Document No: <docno> - Reverted Successfully."}; this constant deliberately
     * omits the dynamic "Document No: X - " prefix, same convention as
     * {@link #REVERT_ALREADY_UNPOSTED_INFO}. Confirmed via a live screenshot — this dialog was
     * previously never dismissed by {@code GeneralVoucherMainGrid.confirmRevert()}, leaving it
     * open and blocking the grid for any caller that needs to keep interacting with the list
     * page right after un-posting (e.g. {@code EditGeneralVoucher}).
     */
    REVERT_SUCCESS_RESULT(
            "Reverted Successfully.",
            PopupType.INFO
    ),

    /**
     * Appears right after clicking Save, before the final success message — asks whether to
     * release the document immediately as part of this save. Yes/No buttons.
     *
     * <p><b>CONFIRMED role-dependent</b> (live observation, Edit General Voucher): does not
     * appear for every user role — some roles' save flow never asks (auto-releases, or never
     * releases at all), so this dialog is genuinely optional per-run, not guaranteed, and must
     * be detected defensively (present-or-absent) rather than always expected — same idiom
     * already used for the TDS Auto-Journal Continue prompt in {@code DocumentPopup
     * #handleTdsAutoJournalPromptIfPresent}.
     *
     * <p>Test flows click <b>Yes</b> when this appears (see {@code DocumentPopup
     * #handleReleaseConfirmationIfPresent}) — default to releasing unless a specific value is
     * provided (plan §14, per explicit user preference; no such per-scenario override exists
     * yet). Releasing changes {@code release_status}/{@code release_time}/{@code release_ecode}
     * together — the common {@code VoucherEditChangeSets} builder marks all three {@code IGNORE}
     * (not the snapshot engine's usual {@code MUST_NOT_CHANGE} default) precisely because whether
     * this dialog even appears is role-dependent and not something the test data declares.
     */
    RELEASE_AFTER_SAVE_CONFIRMATION(
            "Document will be released after save. Do you want to release?",
            PopupType.CONFIRM
    );

    private final String text;
    private final PopupType expectedType;

    DocumentMessageBoxMessage(String text, PopupType expectedType) {
        this.text = text;
        this.expectedType = expectedType;
    }

    public String getText()            { return text; }
    public PopupType getExpectedType() { return expectedType; }
}
