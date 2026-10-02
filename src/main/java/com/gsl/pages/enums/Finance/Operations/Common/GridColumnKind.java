package com.gsl.pages.enums.Finance.Operations.Common;

/**
 * Whether a voucher grid column is always shown or user-configurable (column-menu show/hide).
 * Drives UI-contract validation: a missing {@link #DEFAULT} column is a FAIL, a missing
 * {@link #USER} column is reported as SKIP ("not displayed"), a present one is validated.
 */
public enum GridColumnKind {
    /** Always displayed — absence is a failure. */
    DEFAULT,
    /** Optional, shown/hidden by the user via the grid column menu — validated only if displayed. */
    USER
}
