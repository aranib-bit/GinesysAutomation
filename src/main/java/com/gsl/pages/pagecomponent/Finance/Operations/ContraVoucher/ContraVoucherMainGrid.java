package com.gsl.pages.pagecomponent.Finance.Operations.ContraVoucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.ContraVoucher.ContraVoucherListColumn;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherMainGridBase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main data-grid for Contra Voucher's list page — mirrors {@code GeneralVoucherMainGrid}.
 *
 * <p>TODO: placeholder only — see {@code ContraVoucherListGridHeader}. {@link #allColumns()}
 * currently returns only the shared common columns since {@code ContraVoucherListColumn} has
 * no confirmed constants yet.
 */
public class ContraVoucherMainGrid extends VoucherMainGridBase {

    public ContraVoucherMainGrid() {
        super(VoucherDomain.CONTRA);
    }

    /**
     * All list-grid columns for Contra Voucher — shared common columns (resolved through
     * {@link VoucherDomain#CONTRA}'s override/exclusion hooks) plus Contra-specific ones.
     */
    public List<IVoucherListColumn> allColumns() {
        List<IVoucherListColumn> columns = new ArrayList<>();
        for (CommonVoucherListColumn common : CommonVoucherListColumn.values()) {
            common.resolveFor(VoucherDomain.CONTRA).ifPresent(columns::add);
        }
        columns.addAll(Arrays.asList(ContraVoucherListColumn.values()));
        return columns;
    }
}
