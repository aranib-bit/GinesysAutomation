package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListGridHeaderBase;

/** Thin list-page header/toolbar for AP Voucher — mirrors {@code GeneralVoucherListGridHeader}. */
public class APVoucherListGridHeader extends VoucherListGridHeaderBase {
    public APVoucherListGridHeader() {
        super(VoucherDomain.AP);
    }
}
