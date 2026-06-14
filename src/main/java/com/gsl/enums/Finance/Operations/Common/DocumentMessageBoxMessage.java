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
