package com.gsl.testdata.Finance.Operations.Vouchers.APVoucher;

import com.gsl.enums.Finance.Operations.APVoucher.OnAccountClearConfirmAction;
import lombok.Setter;

/**
 * Holds optional fields that must not be scanned by XlsxReader's field mapper.
 * XlsxReader calls getDeclaredFields() on the concrete target class only — inherited
 * fields are invisible to it, so these fields never appear as required Excel columns.
 */
public abstract class DocumentInformationBase {

    /**
     * Controls which button to click on the On Account warning popup.
     * Absent from Excel sheets — populated post-load by
     * ExcelApVoucherDataSource via setOnAccountConfirm().
     *
     * Kept in the base class so XlsxReader never sees it and does not look
     * for a matching Excel column.
     */
    @Setter
    private String onAccountConfirm;

    /** Resolves the raw {@link #onAccountConfirm} string into an {@link OnAccountClearConfirmAction}. */
    public OnAccountClearConfirmAction getOnAccountConfirmAction() {
        return OnAccountClearConfirmAction.fromString(onAccountConfirm);
    }
}
