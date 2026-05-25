package com.gsl.pages.enums.Finance.Operations.APVoucher;

public enum ApPaymentInfoLabel {

    CHEQUE_LABEL("Cheque label:");

    private final String label;

    ApPaymentInfoLabel(String label) { this.label = label; }

    public String label() { return label; }
}
