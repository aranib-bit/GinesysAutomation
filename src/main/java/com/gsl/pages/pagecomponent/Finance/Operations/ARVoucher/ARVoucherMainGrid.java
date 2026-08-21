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
}
