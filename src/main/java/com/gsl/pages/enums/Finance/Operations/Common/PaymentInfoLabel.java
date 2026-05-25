package com.gsl.pages.enums.Finance.Operations.Common;

public enum PaymentInfoLabel {

    PAYMENT_MODE("Payment Mode:"),
    INSTRUMENT_NO("Instrument No.:"),
    INSTRUMENT_DATE("Instrument Date:"),
    REFERENCE("Reference:");

    private final String label;

    PaymentInfoLabel(String label) { this.label = label; }

    public String label() { return label; }
}
