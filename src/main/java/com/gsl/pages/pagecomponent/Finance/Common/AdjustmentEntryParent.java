package com.gsl.pages.pagecomponent.Finance.Common;

import com.gsl.enums.VoucherDomain;

/**
 * Contract for a page component that opens an Adjustment Entry popup (implemented by {@link
 * com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase}, the
 * shared base for AR/AP/GV's Sub-Ledger Information grids). Unlike the pure marker interfaces
 * {@link CashBankParent}/{@link TdsNameParent}, this one declares {@link #voucherDomain()} because
 * {@code AdjustmentEntry} needs to know which voucher domain opened it to render itself correctly.
 */
public interface AdjustmentEntryParent {
    /** AP/AR/etc. context this Adjustment Entry was opened from — some popups render
     *  different title text depending on the voucher domain. */
    VoucherDomain voucherDomain();
}
