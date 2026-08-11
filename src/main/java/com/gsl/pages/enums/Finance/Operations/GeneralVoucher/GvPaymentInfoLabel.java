package com.gsl.pages.enums.Finance.Operations.GeneralVoucher;

/**
 * UNVERIFIED — General Voucher's Payment Window has never been confirmed against the live
 * UI (see {@link com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherPaymentVariant}'s
 * class Javadoc). {@code DRAWN_ON} is a best-effort guess mirroring {@link
 * com.gsl.pages.enums.Finance.Operations.ARVoucher.ArPaymentInfoLabel}'s wording — fix this
 * label first if {@link com.gsl.pages.Finance.Operations.Common.GvPaymentInformationPopup}
 * fails to locate the field on the first live run.
 */
public enum GvPaymentInfoLabel {

    DRAWN_ON("Drawn on:");

    private final String label;

    GvPaymentInfoLabel(String label) { this.label = label; }

    public String label() { return label; }
}
