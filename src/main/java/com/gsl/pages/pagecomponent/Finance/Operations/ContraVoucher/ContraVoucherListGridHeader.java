package com.gsl.pages.pagecomponent.Finance.Operations.ContraVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherListGridHeaderBase;

/**
 * Thin list-page header/toolbar for Contra Voucher — mirrors {@code GeneralVoucherListGridHeader}.
 *
 * <p>TODO: placeholder only — Contra Voucher has no live list page/DOM today (menu-placeholder
 * only). Wire up a {@code ContraVoucherListPage} facade once one exists to confirm against.
 */
public class ContraVoucherListGridHeader extends VoucherListGridHeaderBase {
    public ContraVoucherListGridHeader() {
        super(VoucherDomain.CONTRA);
    }
}
