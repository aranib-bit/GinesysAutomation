package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;
import org.openqa.selenium.By;

/**
 * General Voucher-specific Payment Information popup for a <b>Cr-side (Credit/Receipt)</b>
 * Cash/Bank line — see {@link com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher.GeneralVoucherExpenseIncomeDetail
 * #createPaymentPopup(boolean)} for the Dr/Cr dispatch. {@link GvPaymentInformationPopup} is this
 * class's Dr-side (Debit/Payment) sibling, used instead for a Dr line.
 *
 * <p>Mirrors {@link ArPaymentInformationPopup}'s simple shape (plain sendKeys+Tab via {@link
 * PaymentInformationPopup#setInstrumentNoPlain}, no cheque-register known/unknown branching) — a
 * received cheque has no company cheque-book concept at all, confirmed live in idealdb:
 * VG/02597/26-27's Cr-side {@code finvchdet} row has {@code bookcode=NULL} and no matching {@code
 * finchqdet} row whatsoever, exactly like AR Voucher (contrast VG/02556/26-27, the Dr-side
 * sibling scenario, which has both).
 *
 * <p>Reuses {@link GvPaymentInformationPopup#OK_BUTTON_SCOPED} rather than duplicating it — the
 * GV-window-scoped OK button fix is unrelated to Dr/Cr, both GV popups are the same "Payment
 * Information" window.
 */
public class GvReceiptPaymentInformationPopup extends PaymentInformationPopup {

    public GvReceiptPaymentInformationPopup() {
        super(VoucherDomain.GV);
    }

    @Override
    public GvReceiptPaymentInformationPopup setInstrumentNo(String instrumentNo) {
        return (GvReceiptPaymentInformationPopup) setInstrumentNoPlain(instrumentNo);
    }

    @Override
    protected By okButtonLocator() {
        return GvPaymentInformationPopup.OK_BUTTON_SCOPED;
    }
}
