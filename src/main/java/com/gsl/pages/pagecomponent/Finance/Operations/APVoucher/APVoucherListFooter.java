package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListFooterBase;

/** Thin list-page pagination footer for AP Voucher — mirrors {@code GeneralVoucherListFooter}. */
public class APVoucherListFooter extends VoucherListFooterBase {
    public APVoucherListFooter() {
        super(VoucherDomain.AP);
    }
}
