package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Default UI label text for each field in the Voucher Document Information header
 * and the Calculate TDS &amp; Net Payable popup.
 * VoucherDomain implements this interface; a domain constant overrides only the
 * methods whose label differs from the application-wide default.
 */
public interface VoucherDocumentLabels {

    // ── Document Information header ───────────────────────────────────────────
    default String getLabelVoucherNo()  { return "Voucher No.:"; }
    default String getLabelDate()       { return "Date:"; }
    default String getLabelReference()  { return "Reference:"; }
    default String getLabelCashBank()   { return "Cash/Bank:"; }
    default String getLabelRemarks()    { return "Remarks:"; }

    // ── Calculate TDS & Net Payable popup ─────────────────────────────────────
    default String getLabelTdsName()              { return "TDS Name:"; }
    default String getLabelGrossPayable()         { return "Gross Payable:"; }
    default String getLabelTdsApplicableAmount()  { return "TDS Applicable Amount:"; }
    default String getLabelTdsAmount()            { return "TDS Amount:"; }
    default String getLabelPopulateTdsException() { return "Populate TDS Exception Rate"; }
    default String getLabelNetPayable()           { return "Net Payable:"; }

    /** The @id fragment that identifies the Date input widget (differs per domain). */
    String getDateFieldId();
}
