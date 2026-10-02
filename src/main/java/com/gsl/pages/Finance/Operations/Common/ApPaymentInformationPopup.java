package com.gsl.pages.Finance.Operations.Common;

import com.gsl.enums.VoucherDomain;

/**
 * AP Voucher-specific Payment Information popup — the known/unknown cheque-register branching
 * (Instrument No./Instrument Date/warning-dialog handling) lives in the shared
 * {@link ChequeRegisterPaymentInformationPopup}. Its "Cheque label:" field (AP's own name for the
 * "Drawn On" concept) is now handled by the shared {@link PaymentInformationPopup#setDrawnOn(String)},
 * driven by {@link VoucherDomain#getPaymentDrawnOnLabel()} — this class has nothing left to
 * override beyond supplying its own domain.
 */
public class ApPaymentInformationPopup extends ChequeRegisterPaymentInformationPopup {

    public ApPaymentInformationPopup() {
        super(VoucherDomain.AP);
    }
}
