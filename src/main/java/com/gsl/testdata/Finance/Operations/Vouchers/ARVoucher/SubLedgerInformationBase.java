package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.enums.Finance.Operations.APVoucher.DeductTdsClearConfirmAction;
import lombok.Getter;
import lombok.Setter;

/**
 * Holds runtime-resolved fields that must not be scanned by XlsxReader's field mapper.
 * XlsxReader calls getDeclaredFields() on the concrete target class only — inherited
 * fields are invisible to it, so these fields never appear as required Excel columns.
 */
public abstract class SubLedgerInformationBase {

    @Getter
    @Setter
    private String slType;

    /**
     * Controls which button to click on the TDS confirmation popup when Deduct TDS
     * changes from Yes → No. Absent from Excel sheets — populated post-load by
     * ExcelApVoucherDataSource via setDeductTdsConfirm().
     *
     * Kept in the base class so XlsxReader (getDeclaredFields on the concrete class)
     * never sees it and does not look for a matching Excel column.
     */
    @Setter
    private String deductTdsConfirm;

    /**
     * When {@code true}, Pass 2 of the SL fill flow actively sets (overwrites) the
     * Reference Site cell for this row. Defaults to {@code false} — Pass 2 skips the
     * cell so whatever the app already populated in Pass 1 is left untouched.
     *
     * Set to {@code true} only in regression scenarios that specifically test changing
     * an existing Reference Site value.
     */
    @Getter
    @Setter
    private boolean changeReferenceSite = false;

    public DeductTdsClearConfirmAction getDeductTdsConfirmAction() {
        return DeductTdsClearConfirmAction.fromString(deductTdsConfirm);
    }
}
