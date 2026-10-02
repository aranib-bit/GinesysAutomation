package com.gsl.pages.pagecomponent.Finance.Operations.Common;

import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;

/**
 * Post-Revert / post-Delete confirmation dialogs shared by AR and AP Voucher.
 *
 * <p>Both vouchers use the same custom Fin.js windows (not General Voucher's plain Yes/No
 * {@code Ext.Msg.confirm}): Revert's confirm button reads "Revert", Delete's reads "Delete Anyway",
 * and each is followed by a "Result" dialog in the same reused {@code messagebox-1001} element —
 * confirmed by live DOM captures for AP (runs 20260927_173428 / 20260929_171715); AR's own
 * {@code doRevert()} is the same shared {@code Fin.js} function (see the plan's Round 2 evidence).
 * The logic lived only in {@code APVoucherMainGrid}; it is extracted here so AR no longer needs
 * the placeholder {@code //TODO} unpost/delete locators. Each grid maps {@link RevertDialogResult}
 * onto its own domain outcome enum.
 */
public final class ArApVoucherDialogs {

    private ArApVoucherDialogs() {}

    /** Domain-neutral outcome of {@link #confirmRevert()} — mirrors the 4 shared outcomes. */
    public enum RevertDialogResult {
        UNPOSTED, ALREADY_UNPOSTED, CANNOT_REVERT_BRS_DONE, CANNOT_REVERT_MONTH_CLOSED
    }

    /** Confirms whichever post-Revert dialog appears and dismisses the follow-up Result. */
    public static RevertDialogResult confirmRevert() {
        DocumentMessageBox msgBox = new DocumentMessageBox();
        String messageText = msgBox.getMessageText();

        if (messageText.contains(DocumentMessageBoxMessage.REVERT_UNPOST_CONFIRMATION.getText())) {
            msgBox.clickRevert();
            JavascriptExecutorUtils.waitForExtJS();
            // The message box element is reused — wait for its text to leave the confirm wording
            // before reading the Result (same race fixed for Delete below).
            msgBox.waitForMessageToChangeFrom(DocumentMessageBoxMessage.REVERT_UNPOST_CONFIRMATION);
            DocumentMessageBox resultBox = new DocumentMessageBox();
            String resultText = resultBox.getMessageText();
            if (resultText.contains(DocumentMessageBoxMessage.REVERT_MONTH_ALREADY_CLOSED.getText())) {
                resultBox.clickOk();
                JavascriptExecutorUtils.waitForExtJS();
                return RevertDialogResult.CANNOT_REVERT_MONTH_CLOSED;
            }
            if (!resultText.contains(DocumentMessageBoxMessage.REVERT_SUCCESS_RESULT.getText())) {
                throw new IllegalStateException(
                        "ArApVoucherDialogs.confirmRevert(): expected the '"
                        + DocumentMessageBoxMessage.REVERT_SUCCESS_RESULT.getText() + "' or '"
                        + DocumentMessageBoxMessage.REVERT_MONTH_ALREADY_CLOSED.getText()
                        + "' Result dialog after confirming Revert, but found: [" + resultText + "].");
            }
            resultBox.clickOk();
            JavascriptExecutorUtils.waitForExtJS();
            return RevertDialogResult.UNPOSTED;
        }
        if (messageText.contains(DocumentMessageBoxMessage.REVERT_ALREADY_UNPOSTED_INFO.getText())) {
            msgBox.clickOk();
            JavascriptExecutorUtils.waitForExtJS();
            return RevertDialogResult.ALREADY_UNPOSTED;
        }
        if (messageText.contains(DocumentMessageBoxMessage.REVERT_BRS_ALREADY_DONE.getText())) {
            msgBox.clickOk();
            JavascriptExecutorUtils.waitForExtJS();
            return RevertDialogResult.CANNOT_REVERT_BRS_DONE;
        }
        throw new IllegalStateException(
                "ArApVoucherDialogs.confirmRevert(): unrecognized post-Revert dialog text: ["
                + messageText + "]. Expected one of '" + DocumentMessageBoxMessage.REVERT_UNPOST_CONFIRMATION.getText()
                + "', '" + DocumentMessageBoxMessage.REVERT_ALREADY_UNPOSTED_INFO.getText()
                + "', or '" + DocumentMessageBoxMessage.REVERT_BRS_ALREADY_DONE.getText() + "'.");
    }

    /** Confirms the Delete dialog ("Delete Anyway") and dismisses the follow-up Result. */
    public static void confirmDelete() {
        DocumentMessageBox msgBox = new DocumentMessageBox();
        msgBox.verifyMessage(DocumentMessageBoxMessage.DELETE_CONFIRMATION);
        msgBox.clickDeleteAnyway();
        JavascriptExecutorUtils.waitForExtJS();

        DocumentMessageBox resultBox = new DocumentMessageBox();
        // Reused message box: its old confirm text stays on screen until the server answers —
        // a slow (e.g. previously-tagged) delete lost this race in run 20260930_163632.
        resultBox.waitForMessageToContain(DocumentMessageBoxMessage.DELETE_SUCCESS_RESULT);
        resultBox.verifyMessage(DocumentMessageBoxMessage.DELETE_SUCCESS_RESULT);
        resultBox.clickOk();
        JavascriptExecutorUtils.waitForExtJS();
    }
}
