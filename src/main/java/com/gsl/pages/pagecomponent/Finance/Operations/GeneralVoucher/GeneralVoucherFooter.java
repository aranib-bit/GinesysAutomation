package com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherFooterBase;

/**
 * Thin Save/Close footer for General Voucher — mirrors {@code APVoucherFooter}.
 * The Net Amount/Dr-Cr/Released badge shown in the screenshot belong on
 * {@link GeneralVoucherDocumentInformation}, not here — {@code VoucherFooterBase}
 * is Save/Close-only, and nothing suggests GV's Save/Close mechanics differ
 * from AP/AR's.
 */
public class GeneralVoucherFooter extends VoucherFooterBase {
    public GeneralVoucherFooter() {
        super(VoucherDomain.GV);
    }
}
