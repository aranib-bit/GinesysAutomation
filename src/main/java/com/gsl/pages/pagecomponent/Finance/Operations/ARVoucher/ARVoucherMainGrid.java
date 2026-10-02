package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherMainGridBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Main data-grid for AR Voucher's list page — mirrors {@code GeneralVoucherMainGrid}. */
public class ARVoucherMainGrid extends VoucherMainGridBase {

    public ARVoucherMainGrid() {
        super(VoucherDomain.AR);
    }

    /**
     * All list-grid columns for AR Voucher — shared common columns (resolved through
     * {@link VoucherDomain#AR}'s override/exclusion hooks, so a future divergence doesn't
     * require touching this method) plus AR-specific ones.
     */
    public List<IVoucherListColumn> allColumns() {
        List<IVoucherListColumn> columns = new ArrayList<>();
        for (CommonVoucherListColumn common : CommonVoucherListColumn.values()) {
            common.resolveFor(VoucherDomain.AR).ifPresent(columns::add);
        }
        columns.addAll(Arrays.asList(ARVoucherListColumn.values()));
        return columns;
    }

    /**
     * Confirms whichever post-Revert dialog appears. AR Voucher shares AP's custom Fin.js
     * {@code doRevert()} dialogs (confirm button "Revert", then a "Result" dialog) — see
     * {@link com.gsl.pages.pagecomponent.Finance.Operations.Common.ArApVoucherDialogs}.
     * Replaces the never-confirmed {@code //TODO-unpost-button} locator AR tests used to hit.
     */
    @com.gsl.annotations.LogStep("Confirm Revert")
    public com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherRevertOutcome confirmRevert() {
        switch (com.gsl.pages.pagecomponent.Finance.Operations.Common.ArApVoucherDialogs.confirmRevert()) {
            case UNPOSTED:                   return com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherRevertOutcome.UNPOSTED;
            case ALREADY_UNPOSTED:           return com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherRevertOutcome.ALREADY_UNPOSTED;
            case CANNOT_REVERT_BRS_DONE:     return com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherRevertOutcome.CANNOT_REVERT_BRS_DONE;
            case CANNOT_REVERT_MONTH_CLOSED: return com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED;
            default: throw new IllegalStateException("Unmapped revert dialog result");
        }
    }

    /** AR Delete uses the same "Delete Anyway" + Result dialogs as AP. */
    @com.gsl.annotations.LogStep("Confirm Delete Dialog")
    @Override
    public void confirmDelete() {
        com.gsl.pages.pagecomponent.Finance.Operations.Common.ArApVoucherDialogs.confirmDelete();
    }
}
