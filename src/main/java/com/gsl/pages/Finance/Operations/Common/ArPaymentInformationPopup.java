package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;

/**
 * AR Voucher-specific Payment Information popup.
 *
 * Instrument No: plain sendKeys+Tab — see {@link PaymentInformationPopup#setInstrumentNoPlain},
 * shared with {@link GvReceiptPaymentInformationPopup} — the app auto-populates Instrument Date
 * directly, no cheque-register known/unknown lookup at all.
 *
 * <p>"Drawn On" field handling (label + locator) now lives in the shared {@link
 * PaymentInformationPopup#setDrawnOn(String)}, driven by {@link VoucherDomain#getPaymentDrawnOnLabel()}
 * — AR no longer needs its own override (its label, "Drawn on:", is centralized there now).
 */
public class ArPaymentInformationPopup extends PaymentInformationPopup {

    public ArPaymentInformationPopup() {
        super(VoucherDomain.AR);
    }

    @Override
    public ArPaymentInformationPopup setInstrumentNo(String instrumentNo) {
        return (ArPaymentInformationPopup) setInstrumentNoPlain(instrumentNo);
    }
}
