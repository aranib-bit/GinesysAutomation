package com.gsl.pages.enums.Finance.Operations.General;

import com.gsl.utils.Selenium.ExtJsUtils;

/**
 * Field heading texts for ExtJS radio groups on the Voucher Document Information form.
 * Pass to {@link ExtJsUtils#selectExtJsRadio(ExtJsUtils.RadioFieldLabel, String)} and
 * {@link ExtJsUtils#getSelectedExtJsRadioLabel(ExtJsUtils.RadioFieldLabel)}.
 */
public enum VoucherDocumentFormLabel implements ExtJsUtils.RadioFieldLabel {

    ON_ACCOUNT("On Account:");

    private final String fieldLabel;

    VoucherDocumentFormLabel(String fieldLabel) {
        this.fieldLabel = fieldLabel;
    }

    @Override
    public String getFieldLabel() {
        return fieldLabel;
    }
}
