package com.gsl.pages.pagecomponent.Finance.Operations.GeneralVoucher;

import com.gsl.annotations.LogStep;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.enums.CommonButton;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherActionsMenu;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridAction;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.VoucherRevertOutcome;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherMainGridBase;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.enums.WaitType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main data-grid for General Voucher's list page — mirrors {@code GeneralVoucherFooter}'s
 * thin-subclass shape, plus keeps the GV-specific Revert-from-List flow (confirmed against
 * the live DOM), which AR/AP don't have yet.
 */
public class GeneralVoucherMainGrid extends VoucherMainGridBase {

    public GeneralVoucherMainGrid() {
        super(VoucherDomain.GV);
    }

    /**
     * All list-grid columns for General Voucher — shared common columns (resolved through
     * {@link VoucherDomain#GV}'s override/exclusion hooks, so a future divergence doesn't
     * require touching this method) plus GV-specific ones.
     */
    public List<IVoucherListColumn> allColumns() {
        List<IVoucherListColumn> columns = new ArrayList<>();
        for (CommonVoucherListColumn common : CommonVoucherListColumn.values()) {
            common.resolveFor(VoucherDomain.GV).ifPresent(columns::add);
        }
        columns.addAll(Arrays.asList(GeneralVoucherListColumn.values()));
        return columns;
    }

    /**
     * All Actions-dropdown items for General Voucher — shared common actions (resolved through
     * {@link VoucherDomain#GV}'s override/exclusion hooks, so a future divergence doesn't require
     * touching this method) — GV has no domain-specific extra action of its own. Mirrors
     * {@link #allColumns()}'s exact shape (plan §9.3).
     */
    public List<IVoucherGridAction> allActions() {
        List<IVoucherGridAction> actions = new ArrayList<>();
        for (CommonVoucherActionsMenu common : CommonVoucherActionsMenu.values()) {
            common.resolveFor(VoucherDomain.GV).ifPresent(actions::add);
        }
        return actions;
    }

    /**
     * {@code clickEdit()}/{@code clickRevert()} moved to {@link VoucherMainGridBase} (plan
     * §12) — inherited from there now, since both were trivial domain-hardcoded wrappers with
     * no GV-specific logic once {@code VoucherMainGridBase} owned its own {@code domain} field
     * and {@link VoucherMainGridBase#clickGridAction(IVoucherGridAction)}. Only
     * {@link #confirmRevert()} stays here — its dialog-text matching is confirmed only against
     * GV's live DOM.
     */

    /**
     * Confirms whichever post-Revert dialog appears — completes the operation. Reads the
     * message box text and branches: a currently Posted document shows the "will be un-posted,
     * proceed?" Yes/No dialog (click Yes); a document already un-posted, or one whose line(s)
     * are already bank-reconciled (BRS), instead shows a single-button informational message
     * (click OK, no state change). Returns which case actually happened so the caller can assert
     * the correct expected DB outcome for whichever starting state applies.
     */
    @LogStep("Confirm Revert")
    public VoucherRevertOutcome confirmRevert() {
        DocumentMessageBox msgBox = new DocumentMessageBox();
        String messageText = msgBox.getMessageText();

        if (messageText.contains(DocumentMessageBoxMessage.REVERT_UNPOST_CONFIRMATION.getText())) {
            SeleniumUtils.click(CommonButton.YES.locator(), WaitType.CLICKABLE, "GENERALVOUCHER_REVERT_CONFIRM_YES");
            JavascriptExecutorUtils.waitForExtJS();
            // A SEPARATE "Result" dialog follows the Yes confirmation (confirmed via a live
            // screenshot) — previously never dismissed here, leaving it open and blocking the
            // grid for any caller that needs to keep interacting with the list page right after
            // un-posting (e.g. EditGeneralVoucher). Must be closed before returning.
            //
            // Live evidence (report runs 20260831_140246 and 20260831_163724) confirms this
            // Result dialog is where a closed-accounting-period rejection actually surfaces —
            // NOT as an alternative to the first REVERT_UNPOST_CONFIRMATION dialog above, since
            // the app only discovers the period is closed once Revert is actually attempted
            // after Yes is clicked. A top-level check for REVERT_MONTH_ALREADY_CLOSED (checked
            // against the FIRST dialog's text) never fired for this reason — it must be checked
            // here, against resultText, before the generic "not a success" throw below.
            DocumentMessageBox resultBox = new DocumentMessageBox();
            String resultText = resultBox.getMessageText();
            if (resultText.contains(DocumentMessageBoxMessage.REVERT_MONTH_ALREADY_CLOSED.getText())) {
                resultBox.clickOk();
                JavascriptExecutorUtils.waitForExtJS();
                return VoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED;
            }
            if (!resultText.contains(DocumentMessageBoxMessage.REVERT_SUCCESS_RESULT.getText())) {
                throw new IllegalStateException(
                        "GeneralVoucherMainGrid.confirmRevert(): expected the '"
                        + DocumentMessageBoxMessage.REVERT_SUCCESS_RESULT.getText() + "' or '"
                        + DocumentMessageBoxMessage.REVERT_MONTH_ALREADY_CLOSED.getText()
                        + "' Result dialog after confirming Yes, but found: [" + resultText + "].");
            }
            resultBox.clickOk();
            JavascriptExecutorUtils.waitForExtJS();
            return VoucherRevertOutcome.UNPOSTED;
        }
        if (messageText.contains(DocumentMessageBoxMessage.REVERT_ALREADY_UNPOSTED_INFO.getText())) {
            msgBox.clickOk();
            JavascriptExecutorUtils.waitForExtJS();
            return VoucherRevertOutcome.ALREADY_UNPOSTED;
        }
        if (messageText.contains(DocumentMessageBoxMessage.REVERT_BRS_ALREADY_DONE.getText())) {
            msgBox.clickOk();
            JavascriptExecutorUtils.waitForExtJS();
            return VoucherRevertOutcome.CANNOT_REVERT_BRS_DONE;
        }
        throw new IllegalStateException(
                "GeneralVoucherMainGrid.confirmRevert(): unrecognized post-Revert dialog text: ["
                + messageText + "]. Expected one of '" + DocumentMessageBoxMessage.REVERT_UNPOST_CONFIRMATION.getText()
                + "', '" + DocumentMessageBoxMessage.REVERT_ALREADY_UNPOSTED_INFO.getText()
                + "', or '" + DocumentMessageBoxMessage.REVERT_BRS_ALREADY_DONE.getText() + "'.");
    }
}
