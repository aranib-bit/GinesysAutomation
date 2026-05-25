package com.gsl.pages.enums.Finance.Operations.ARVoucher;

public enum ArPaymentInfoLabel {

    DRAWN_ON("Drawn on:");

    private final String label;

    ArPaymentInfoLabel(String label) { this.label = label; }

    public String label() { return label; }
}
