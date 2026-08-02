package com.gsl.pages.pagecomponent.Finance.Common;

import com.gsl.enums.VoucherDomain;

public interface AdjustmentEntryParent {
    /** AP/AR/etc. context this Adjustment Entry was opened from — some popups render
     *  different title text depending on the voucher domain. */
    VoucherDomain voucherDomain();
}
