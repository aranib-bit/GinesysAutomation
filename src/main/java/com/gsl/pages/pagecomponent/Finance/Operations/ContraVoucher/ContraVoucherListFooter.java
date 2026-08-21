package com.gsl.pages.pagecomponent.Finance.Operations.ContraVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListFooterBase;

/**
 * Thin list-page pagination footer for Contra Voucher — mirrors {@code GeneralVoucherListFooter}.
 *
 * <p>TODO: placeholder only — see {@code ContraVoucherListGridHeader}.
 */
public class ContraVoucherListFooter extends VoucherListFooterBase {
    public ContraVoucherListFooter() {
        super(VoucherDomain.CONTRA);
    }
}
