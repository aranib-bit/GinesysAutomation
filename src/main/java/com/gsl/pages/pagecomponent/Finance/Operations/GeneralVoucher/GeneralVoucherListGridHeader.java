package com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListGridHeaderBase;

/** Thin list-page header/toolbar for General Voucher — mirrors {@code GeneralVoucherFooter}. */
public class GeneralVoucherListGridHeader extends VoucherListGridHeaderBase {
    public GeneralVoucherListGridHeader() {
        super(VoucherDomain.GV);
    }
}
