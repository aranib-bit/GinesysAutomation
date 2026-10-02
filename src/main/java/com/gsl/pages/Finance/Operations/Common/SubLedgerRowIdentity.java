package com.gsl.pages.Finance.Operations.Common;

import lombok.Value;

/**
 * Immutable snapshot of one Sub-Ledger grid row's own identifying display text (Sub-Ledger name,
 * Reference Site, Sub Ledger Id) — read straight off the rendered grid via
 * {@code VoucherSubLedgerInformationBase#readRowIdentity(int)}, the inverse of the same
 * {@code data-columnid}-scoped locating shape {@code clickAdjust} already uses to find a row BY
 * these same three values. Exists so a caller that only knows a row's position (not its identity —
 * e.g. re-tagging an already-populated Edit-mode row) can discover the values {@code clickAdjust}/
 * {@code openAdjustmentEntryForExistingRow} need, without a DB join.
 */
@Value
public class SubLedgerRowIdentity {
    String subLedgerName;
    String referenceSite;
    String slId;
}
