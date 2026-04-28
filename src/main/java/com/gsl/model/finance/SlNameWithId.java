package com.gsl.model.finance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Carries the SL name and SL ID pair from v_pending_adj.
 *
 * <p>Duplicate SL names are allowed in the system; uniqueness is the combination
 * of {@code slName} + {@code slId}. Using only {@code slName} can cause the wrong
 * sub-ledger to be selected in the UI when two SLs share the same display name.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SlNameWithId {
    /** Sub-ledger display name (slname column in v_pending_adj). */
    private String slName;
    /** Sub-ledger code / ID (slid column in v_pending_adj). */
    private String slId;
}
