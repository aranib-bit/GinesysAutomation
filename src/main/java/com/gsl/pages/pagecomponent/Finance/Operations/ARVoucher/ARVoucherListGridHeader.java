package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListGridHeaderBase;

/** Thin list-page header/toolbar for AR Voucher — mirrors {@code GeneralVoucherListGridHeader}. */
public class ARVoucherListGridHeader extends VoucherListGridHeaderBase {
    public ARVoucherListGridHeader() {
        super(VoucherDomain.AR);
    }
}
