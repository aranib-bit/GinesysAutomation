package com.gsl.validation.db.finance;

import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherPaymentInfo;

/**
 * Derives the expected finvchdet.payment_mode DB code for any Finance voucher type.
 *
 * Rule:
 *   If PaymentInformation is provided for the line → use its payment mode directly:
 *     NEFT/RTGS → 'R' | Cheque → 'C' | Cash → 'H'
 *   Otherwise fall back to the Cash/Bank GL type:
 *     srctype 'C' (Cash GL) → 'H'
 *     srctype 'B' (Bank GL) → 'C'  (system default Cheque)
 *
 * Used by FinVchDetValidator (AR) and FinVchDetApValidator (AP) so the rule
 * is defined and tested in one place.
 */
public final class PaymentModeResolver {

    private PaymentModeResolver() {}

    public static String resolve(String cashBankSrctype, VoucherPaymentInfo paymentInfo) {
        if (paymentInfo != null && paymentInfo.getPaymentMode() != null)
            return paymentInfo.getPaymentMode().dbCode();

        if ("C".equalsIgnoreCase(cashBankSrctype))
            return PaymentSelectionType.CASH.dbCode();   // 'H'

        return PaymentSelectionType.CHEQUE.dbCode();     // 'C'
    }
}
