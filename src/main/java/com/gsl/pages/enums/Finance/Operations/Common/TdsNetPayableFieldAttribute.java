package com.gsl.pages.enums.Finance.Operations.Common;

public enum TdsNetPayableFieldAttribute {

    /**
     * ExtJS writes the computed value of disabled number fields (e.g. Net Payable)
     * into aria-valuenow rather than the input's value property.
     */
    ARIA_VALUE_NOW("aria-valuenow");

    private final String attribute;

    TdsNetPayableFieldAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String attribute() {
        return attribute;
    }
}
