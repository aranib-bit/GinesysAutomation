package com.gsl.testdata.dto;

/**
 * Which UI operation a {@link MergedFinanceGeneralVoucherData} row drives — replaces the fragile
 * "{@code existingVoucherNo != null} ⇒ reverted" inference {@code GeneralVoucherDescriptionBuilder}
 * used to make, which would otherwise mislabel an Edit run as "reverted successfully" once Edit
 * also populates {@code existingVoucherNo} (plan §5).
 */
public enum GeneralVoucherOperationMode {
    CREATE,
    REVERT,
    EDIT
}
