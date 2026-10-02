package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.annotations.LogStep;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherRevertOutcome;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherMainGridBase;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;

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

    /**
     * Confirms whichever post-Revert dialog appears — completes the operation. Same 4-branch
     * message-text-matching structure as {@code GeneralVoucherMainGrid#confirmRevert()} —
     * {@link DocumentMessageBoxMessage}'s {@code REVERT_*} constants are explicitly documented as
     * shared framework-wide across AP/AR/GV, not GV-specific text, so the exact same constants are
     * reused here rather than new AP-specific ones being invented.
     *
     * <p><b>Confirm-button click — corrected (was {@code CommonButton.YES}, now
     * {@code msgBox.clickRevert()}):</b> a live DOM capture (run 20260927_173428,
     * {@code revertApVoucher_1790511259600.html}) confirmed AP's real post-Revert confirmation is
     * NOT General Voucher's plain Yes/No {@code Ext.Msg.confirm} — it's a custom window titled
     * "Revert AP Voucher" whose confirm button reads "Revert" (no "Yes" button exists anywhere in
     * that dialog's DOM at all). Message-text matching itself was already confirmed correct by that
     * same capture — only the button click needed fixing. See {@link DocumentMessageBox#clickRevert()}
     * and its locator's own Javadoc for the full evidence.
     */
    @LogStep("Confirm Revert")
    public APVoucherRevertOutcome confirmRevert() {
        // Dialog handling is shared with AR Voucher — see ArApVoucherDialogs for the live evidence.
        switch (com.gsl.pages.pagecomponent.Finance.Operations.Common.ArApVoucherDialogs.confirmRevert()) {
            case UNPOSTED:                   return APVoucherRevertOutcome.UNPOSTED;
            case ALREADY_UNPOSTED:           return APVoucherRevertOutcome.ALREADY_UNPOSTED;
            case CANNOT_REVERT_BRS_DONE:     return APVoucherRevertOutcome.CANNOT_REVERT_BRS_DONE;
            case CANNOT_REVERT_MONTH_CLOSED: return APVoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED;
            default: throw new IllegalStateException("Unmapped revert dialog result");
        }
    }

    /**
     * Confirms the Delete confirmation dialog, then dismisses the follow-up "Result" dialog —
     * overrides {@link VoucherMainGridBase#confirmDelete()}, whose own Javadoc confirms it was
     * verified live for **General Voucher only** (2026-09-01). AP's dialog genuinely differs, same
     * "custom Fin.js dialog, not GV's plain Ext.Msg.confirm" pattern already confirmed for Revert
     * above: a live DOM capture (run 20260929_171715, {@code deleteApVoucher_1790683008966.html})
     * showed the message text matches {@link DocumentMessageBoxMessage#DELETE_CONFIRMATION} exactly,
     * but the visible confirm button reads "Delete Anyway" — no "Yes" button exists in that dialog's
     * DOM at all (siblings in the same toolbar: a hidden "OK" for the second/result stage, a visible
     * "Close", and a hidden "Cancel"). See {@link DocumentMessageBox#clickDeleteAnyway()} and its
     * locator's own Javadoc for the full evidence.
     *
     * <p><b>Honest caveat:</b> only the first-stage button was DOM-confirmed wrong — the second
     * stage "Result" dialog's exact text was never reached live (the run failed before getting
     * there), so reusing {@link DocumentMessageBoxMessage#DELETE_SUCCESS_RESULT} unchanged is a
     * reasonable bet (AP's own Revert dialog reuses the same "OK"-button result shape), not a
     * confirmed fact. If a future run fails at the result-dialog stage instead, that's new evidence
     * to act on.
     */
    @LogStep("Confirm Delete Dialog")
    @Override
    public void confirmDelete() {
        com.gsl.pages.pagecomponent.Finance.Operations.Common.ArApVoucherDialogs.confirmDelete();
    }
}
