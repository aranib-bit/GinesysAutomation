package com.gsl.enums.Finance.Operations.APVoucher;

public enum OnAccountValues {
    YES("Yes"), NO("No");

    private final String label;

    OnAccountValues(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static OnAccountValues from(boolean b) { return b ? YES : NO; }
}
