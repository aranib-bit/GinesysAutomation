package com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListFooterBase;

/** Thin list-page pagination footer for General Voucher — mirrors {@code GeneralVoucherFooter}. */
public class GeneralVoucherListFooter extends VoucherListFooterBase {
    public GeneralVoucherListFooter() {
        super(VoucherDomain.GV);
    }
}
