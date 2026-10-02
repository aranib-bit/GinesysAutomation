package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * DOM attribute(s) used to read computed field values off the Calculate TDS &amp; Net Payable
 * popup — see {@code APVoucherTdsAndNetPayable}'s reads of its TDS Amount and Net Payable
 * fields, both disabled number fields whose value isn't exposed the normal way (see
 * {@link #ARIA_VALUE_NOW}).
 */
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

    /** The DOM attribute name to read, e.g. {@code "aria-valuenow"}. */
    public String attribute() {
        return attribute;
    }
}
