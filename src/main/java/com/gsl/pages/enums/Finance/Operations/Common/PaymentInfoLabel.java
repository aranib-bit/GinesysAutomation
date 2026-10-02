package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Field label texts on the Payment Information popup (shared across AR/AP/GV — the one
 * domain-varying field on that popup, "Drawn On"/"Cheque label", is handled separately by
 * {@code VoucherDomain#getPaymentDrawnOnLabel()}, not here). Used by
 * {@code PaymentInformationPopup} to locate each field's input relative to its label span.
 */
public enum PaymentInfoLabel {

    PAYMENT_MODE("Payment Mode:"),
    INSTRUMENT_NO("Instrument No.:"),
    INSTRUMENT_DATE("Instrument Date:"),
    REFERENCE("Reference:");

    private final String label;

    PaymentInfoLabel(String label) { this.label = label; }

    /** The literal label text as rendered on the popup, e.g. {@code "Payment Mode:"}. */
    public String label() { return label; }
}
