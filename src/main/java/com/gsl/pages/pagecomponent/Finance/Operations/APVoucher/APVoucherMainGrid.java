package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherMainGridBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Main data-grid for AP Voucher's list page — mirrors {@code GeneralVoucherMainGrid}. */
public class APVoucherMainGrid extends VoucherMainGridBase {

    public APVoucherMainGrid() {
        super(VoucherDomain.AP);
    }

    /**
     * All list-grid columns for AP Voucher — shared common columns (resolved through
     * {@link VoucherDomain#AP}'s override/exclusion hooks, so a future divergence doesn't
     * require touching this method) plus AP-specific ones.
     */
    public List<IVoucherListColumn> allColumns() {
        List<IVoucherListColumn> columns = new ArrayList<>();
        for (CommonVoucherListColumn common : CommonVoucherListColumn.values()) {
            common.resolveFor(VoucherDomain.AP).ifPresent(columns::add);
        }
        columns.addAll(Arrays.asList(APVoucherListColumn.values()));
        return columns;
    }
}
