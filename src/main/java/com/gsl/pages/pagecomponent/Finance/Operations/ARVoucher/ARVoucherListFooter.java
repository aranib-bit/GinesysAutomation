package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListFooterBase;

/** Thin list-page pagination footer for AR Voucher — mirrors {@code GeneralVoucherListFooter}. */
public class ARVoucherListFooter extends VoucherListFooterBase {
    public ARVoucherListFooter() {
        super(VoucherDomain.AR);
    }
}
