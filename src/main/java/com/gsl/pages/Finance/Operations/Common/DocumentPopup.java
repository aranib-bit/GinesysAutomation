package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.enums.CommonButton;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

public class DocumentPopup {

    // ── Button text registry ─────────────────────────────────────────────────

    public enum ButtonText {
        IGNORE_AND_PROCEED   ("Ignore & Proceed"),
        OK_GO_AHEAD          ("Ok, Go Ahead"),
        OK_GO_AHEAD_CAPS_START ("OK, Go Ahead"),
        PROCEED_ANYWAY     ("Proceed Anyway"),
        STAY               ("Stay"),
        CLOSE              ("Close");

        private final String label;
        ButtonText(String label) { this.label = label; }
        public String getLabel() { return label; }

        public By locator() {
            return By.xpath("//span[@data-ref='btnInnerEl' and normalize-space()='" + label + "']");
        }
    }

    // ── Fields for Grid>Adj confirmation path ────────────────────────────────

    private final AdjustmentEntryParent gridParent;
    private final AdjustmentEntry       adjustmentEntry;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** Default constructor — used by the save-confirmation path (Ignore & Proceed / message box). */
    public DocumentPopup() {
        this.gridParent      = null;
        this.adjustmentEntry = null;
    }

    /** Constructor for the Grid>Adj confirmation path (Proceed Anyway / Stay). */
    public DocumentPopup(AdjustmentEntryParent gridParent, AdjustmentEntry adjustmentEntry) {
        this.gridParent      = gridParent;
        this.adjustmentEntry = adjustmentEntry;
    }

    // ── Locators ─────────────────────────────────────────────────────────────

    // CONTINUE_BTN → CommonButton.CONTINUE.locator()
    // YES_BUTTON   → CommonButton.YES.locator()
    private static final class Loc {
        static final By MESSAGE_TEXT = By.xpath("//*[@id='messagebox-1001-msg']");
    }

    // ── Save-confirmation path ────────────────────────────────────────────────

    /**
     * Waits for the payment-info-check warning dialog specific to the given voucher type,
     * then reads the message text for diagnostics.
     * Returns {@code this} for fluent chaining with {@link #clickProceed(PaymentInfoCheckWarning)}.
     *
     * <p>AR Voucher: waits for "Ignore &amp; Proceed" button.
     * <p>AP Voucher: waits for "Yes" button.
     */
    public DocumentPopup verifyPaymentInfoCheckWarning(PaymentInfoCheckWarning warning) {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = WaitUtils.webDriverWait();

        ReportLogger.business("Waiting for payment-info-check warning dialog (" + warning + ")...");
        WaitUtils.fluentWaitBuilder().until(ExpectedConditions.visibilityOfElementLocated(warning.proceedLocator()));
        ReportLogger.business("Payment info warning dialog confirmed visible for " + warning);
        com.gsl.reports.ActualResultsHolder.get().setUiPaymentInfoWarningAppeared(true);

        // Non-blocking message-text read for diagnostics — same AOP caveat as before.
        try {
            By msgLocator = By.xpath(
                    "//span[normalize-space()='" + warning.getMessageSubstring().replace("'", "\\'") + "']"
                    + "/ancestor::div[contains(@class,'x-message-box')]"
                    + "//div[contains(@class,'x-component-default')]");
            int maxRetries = ConfigFactory.getWaitConfig().warningMsgMaxRetries();
            WebElement msgEl = null;
            for (int attempt = 0; attempt < maxRetries && msgEl == null; attempt++) {
                List<WebElement> found = driver.findElements(msgLocator);
                if (!found.isEmpty() && found.get(0).isDisplayed()) {
                    msgEl = found.get(0);
                }
            }
            if (msgEl != null)
                ReportLogger.business("Warning popup message: " + msgEl.getAttribute("innerText"));
        } catch (Exception e) {
            System.out.println("Could not read warning message text (non-fatal): " + e.getMessage());
        }

        return this;
    }

    /**
     * Waits for the validation error popup and asserts the message contains the expected text.
     * Used by negative-scenario test cases to confirm that the expected form-validation error
     * is displayed after clicking Save (see {@code ApVoucherPredicateFactory.NEGATIVE_FORM_VALIDATION_BEHAVIOR}).
     *
     * <p>Throws {@link AssertionError} when the popup text does NOT contain {@code expectedMessage}.
     * Returns {@code this} for fluent chaining.
     *
     * @param expectedMessage the substring expected to appear in the validation error popup
     * @return {@code this} for fluent chaining
     */
    /**
     * Clears the payment-info-check warning if — and only if — that is the dialog currently
     * showing, then leaves the next dialog in place for the caller to assert on. A no-op when the
     * dialog on screen is something else (including the validation error the caller wants).
     *
     * <p><b>Why negative flows need this.</b> A General Voucher with a Cash/Bank but no instrument
     * number makes the app raise "This is a payment voucher and you have not specified the
     * instrument number..." <i>before</i> any field validation. Both that warning and the
     * validation error render into the same {@code messagebox-1001}, so
     * {@link #verifyErrorMessage(String)} read the warning and failed the assertion against it —
     * run 20260807_124406: <em>Expected [Sub Ledger: This field is required.] but got [This is a
     * payment voucher and you have not specified the instrument number...]</em>. That run also
     * confirmed the GV wording, which {@link PaymentInfoCheckWarning#GV_VOUCHER} had only guessed
     * at: it is AP's text and AP's Yes/No shape.
     *
     * <p>Gated on the message text containing "instrument number" rather than on the presence of a
     * Yes button — {@code proceedLocator()} is a bare {@code //span[.='Yes']} and
     * {@code GV_VOUCHER}'s substring is just "voucher", either of which could match an unrelated
     * confirm dialog and silently dismiss it.
     *
     * <p>Waits for the message text to actually <i>change</i> afterwards, because the same
     * {@code messagebox-1001} element is reused for the next dialog — waiting on visibility alone
     * would return instantly against the warning's own stale text.
     */
    public DocumentPopup proceedPastPaymentInfoWarningIfPresent(PaymentInfoCheckWarning warning) {
        WebDriver driver = DriverManager.getDriver();
        int shortWait = (int) ConfigFactory.getWaitConfig().shortWaitSeconds();

        if (!SeleniumUtils.isElementPresent(Loc.MESSAGE_TEXT, shortWait)) {
            return this;   // nothing on screen yet — let the caller's own wait handle it
        }
        String current = driver.findElement(Loc.MESSAGE_TEXT).getText();
        if (current == null || !current.toLowerCase().contains("instrument number")) {
            return this;   // a different dialog — leave it for the caller to assert on
        }

        ReportLogger.business("Payment-info warning shown ahead of validation — proceeding past it ("
                + warning + "): " + current);
        com.gsl.reports.ActualResultsHolder.get().setUiPaymentInfoWarningAppeared(true);

        final String warningText = current;
        SeleniumUtils.click(warning.proceedLocator(), WaitType.CLICKABLE, "PAYMENT_WARN_PROCEED");
        WaitUtils.fluentWaitBuilder()
                .withMessage("the message box to change from the payment-info warning to the next dialog")
                .until(d -> {
                    List<WebElement> els = d.findElements(Loc.MESSAGE_TEXT);
                    if (els.isEmpty() || !els.get(0).isDisplayed()) return false;
                    String now = els.get(0).getText();
                    return now != null && !now.isBlank() && !now.equals(warningText);
                });
        return this;
    }

    public String verifyErrorMessage(String expectedMessage) {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = WaitUtils.webDriverWait();

        ReportLogger.business("Waiting for validation error popup to appear...");
        WaitUtils.fluentWaitBuilder().until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));

        // Same role-dependent release-after-save confirmation waitForMessageBox()/clickProceed()
        // already handle — missing here caused a negative date-validation scenario (Edit's
        // "Verify Negative Validation" step) to read THIS confirmation dialog instead of the real
        // validation outcome that only appears after answering it (confirmed live 2026-08-18: DB
        // showed zero actual data change on the claimed voucher, proving no save had committed
        // when this method's assertion fired against the wrong dialog).
        handleReleaseConfirmationIfPresent(driver);

        String actualMessage = driver.findElement(Loc.MESSAGE_TEXT).getText();
        ReportLogger.business("Validation error popup message: " + actualMessage);

        if (actualMessage == null || !actualMessage.contains(expectedMessage)) {
            throw new AssertionError(
                    "Expected validation error popup to contain: [" + expectedMessage + "]"
                    + " but got: [" + actualMessage + "]");
        }
        return actualMessage;
    }

    /**
     * Asserts that the visible error popup's header contains {@code expectedHeader} (case-sensitive,
     * substring match). Trims leading/trailing whitespace from the header element text before
     * comparing — the icon {@code <i>} tag prepends a space in the DOM.
     *
     * @param expectedHeader the substring expected in the popup header (e.g. "Error")
     * @return the trimmed actual header text, for report logging
     */
    public String verifyErrorHeader(String expectedHeader) {
        WebDriver driver = DriverManager.getDriver();
        WaitUtils.fluentWaitBuilder().until(
                ExpectedConditions.visibilityOfElementLocated(By.id("messagebox-1001_header-title-textEl")));
        String actual = driver.findElement(By.id("messagebox-1001_header-title-textEl")).getText().trim();
        if (!actual.contains(expectedHeader)) {
            throw new AssertionError(
                    "Expected error popup header to contain: [" + expectedHeader + "] but got: [" + actual + "]");
        }
        return actual;
    }

    /**
     * Post-save path when payment details are provided — no payment-info-check warning
     * is expected, so this waits directly for the first message box to appear.
     *
     * <p>Popup sequence handled:
     * <ol>
     *   <li>Wait for message box to appear.</li>
     *   <li>If instrument-number warning detected, click proceed and wait for next popup.</li>
     *   <li>If TDS Auto Journal prompt detected (Continue button present), click Continue
     *       and wait for the document-save success popup.</li>
     * </ol>
     */
    public DocumentMessageBox waitForMessageBox() {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = WaitUtils.webDriverWait();

        System.out.println("Waiting for message box after save");
        WaitUtils.fluentWaitBuilder().until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));
        System.out.println("Messagebox is now visible");

        // If the (role-dependent) release-after-save confirmation is showing, click No —
        // see DocumentMessageBoxMessage.RELEASE_AFTER_SAVE_CONFIRMATION's Javadoc.
        handleReleaseConfirmationIfPresent(driver);

        // If the app unexpectedly shows the payment-info-check warning dialog,
        // auto-detect AR vs AP variant and click the appropriate proceed button.
        try {
            String msgText = driver.findElement(Loc.MESSAGE_TEXT).getText();
            if (msgText != null && msgText.contains("instrument number")) {
                PaymentInfoCheckWarning detected = msgText.contains(
                        PaymentInfoCheckWarning.AP_VOUCHER.getMessageSubstring())
                        ? PaymentInfoCheckWarning.AP_VOUCHER
                        : PaymentInfoCheckWarning.AR_VOUCHER;
                System.out.println("Payment info warning detected (" + detected + ") — clicking proceed");
                SeleniumUtils.click(detected.proceedLocator(), WaitType.CLICKABLE, "PAYMENT_WARN_AUTO");
                WaitUtils.fluentWaitBuilder().until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));
                System.out.println("Messagebox visible after payment info proceed.");
            }
        } catch (Exception e) {
            System.out.println("Instrument number dialog check skipped (non-fatal): " + e.getMessage());
        }

        // If TDS Auto Journal prompt is showing, click Continue and wait for success popup.
        handleTdsAutoJournalPromptIfPresent(wait, driver);

        return new DocumentMessageBox();
    }

    /**
     * Clicks the proceed button for the given payment-info-check warning, then waits for
     * the next popup.
     *
     * <p>Popup sequence handled:
     * <ol>
     *   <li>Click the proceed button (Ignore &amp; Proceed for AR; Yes for AP).</li>
     *   <li>Wait for the next message box to appear.</li>
     *   <li>If TDS Auto Journal prompt detected (Continue button present), click Continue
     *       and wait for the document-save success popup.</li>
     * </ol>
     *
     * <p>AR Voucher: clicks "Ignore &amp; Proceed".
     * <p>AP Voucher: clicks "Yes".
     */
    public DocumentMessageBox clickProceed(PaymentInfoCheckWarning warning) {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = WaitUtils.webDriverWait();

        System.out.println("Clicking proceed for " + warning + "...");
        SeleniumUtils.click(warning.proceedLocator(), WaitType.CLICKABLE, "PAYMENT_WARN_PROCEED");

        WaitUtils.fluentWaitBuilder().until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));
        System.out.println("Messagebox is now visible after payment info proceed.");

        // If the (role-dependent) release-after-save confirmation is showing, click No —
        // see DocumentMessageBoxMessage.RELEASE_AFTER_SAVE_CONFIRMATION's Javadoc.
        handleReleaseConfirmationIfPresent(driver);

        // If TDS Auto Journal prompt is showing next, click Continue and wait for success popup.
        handleTdsAutoJournalPromptIfPresent(wait, driver);

        return new DocumentMessageBox();
    }

    /**
     * Clicks the reject button (e.g. "No" on AP Voucher payment warning) and stays on the form.
     * Use for negative test cases that verify the user can cancel the save.
     */
    public void clickReject(PaymentInfoCheckWarning warning) {
        if (!warning.hasRejectButton())
            throw new UnsupportedOperationException(warning + " has no reject button");
        System.out.println("Clicking reject for " + warning + "...");
        SeleniumUtils.click(warning.rejectLocator(), WaitType.CLICKABLE, "PAYMENT_WARN_REJECT");
    }

    /**
     * Clicks the cancel button if available for the given warning type.
     * Provision for future cancel-button scenarios.
     */
    public void clickCancel(PaymentInfoCheckWarning warning) {
        if (!warning.hasCancelButton())
            throw new UnsupportedOperationException(warning + " has no cancel button");
        System.out.println("Clicking cancel for " + warning + "...");
        SeleniumUtils.click(warning.cancelLocator(), WaitType.CLICKABLE, "PAYMENT_WARN_CANCEL");
    }

    // ── Grid>Adj confirmation path (Proceed Anyway / Stay) ───────────────────

    /**
     * Clicks "Proceed Anyway" on the Grid>Adj confirmation popup.
     * Appears when Grid Amount Cr > total Adjustment Entry amount after clicking OK.
     * Closes the popup and returns to the sub-ledger grid.
     */
    public AdjustmentEntryParent clickProceedAnyway() {
        SeleniumUtils.click(ButtonText.PROCEED_ANYWAY.locator(), WaitType.CLICKABLE, "Proceed Anyway");
        return gridParent;
    }

    /**
     * Clicks "Stay" on the Grid>Adj confirmation popup.
     * Dismisses the confirmation and returns to the Adjustment Entry popup.
     */
    public AdjustmentEntry clickStay() {
        SeleniumUtils.click(ButtonText.STAY.locator(), WaitType.CLICKABLE, "Stay");
        return adjustmentEntry;
    }

    /**
     * Clicks the OK button on the validation error popup (after {@link #verifyErrorMessage}).
     * Used by boundary scenarios that need to stay on the form after dismissing the dialog
     * (e.g. to hover the row indicator and read the inline tooltip).
     */
    public void clickOkOnErrorDialog() {
        SeleniumUtils.click(CommonButton.OK.locator(), WaitType.CLICKABLE, "ERROR_DIALOG_OK");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * After a message box becomes visible, checks whether the TDS Auto Journal confirmation
     * prompt is showing by searching for the Continue button.
     *
     * <p>Popup sequence (when TDS Provided = Yes and Auto-Release check was not performed
     * in the testing app):
     * <ol>
     *   <li>Continue button detected → click it.</li>
     *   <li>Wait for the TDS prompt to close.</li>
     *   <li>Wait for the document-save success popup to appear.</li>
     * </ol>
     *
     * <p>If no Continue button is present this method is a no-op — the caller proceeds
     * directly to {@link DocumentMessageBox#verifyDocumentSave}.
     */
    /**
     * After a message box becomes visible, checks whether the (role-dependent) release-after-
     * save confirmation is showing — see {@link DocumentMessageBoxMessage#RELEASE_AFTER_SAVE_CONFIRMATION}'s
     * Javadoc for why this must be checked defensively rather than always expected. If present,
     * clicks <b>Yes</b> (release the document) and waits for the message box text to change to
     * whatever dialog follows — plan §14, per explicit user preference: default to Yes unless a
     * specific value is provided (no such per-scenario override exists yet, so every scenario
     * gets Yes today). A no-op — same {@code messagebox-1001} element simply shows different
     * content next — when this particular dialog never appears at all.
     */
    private void handleReleaseConfirmationIfPresent(WebDriver driver) {
        try {
            List<WebElement> msgEls = driver.findElements(Loc.MESSAGE_TEXT);
            if (msgEls.isEmpty() || !msgEls.get(0).isDisplayed()) return;
            String msgText = msgEls.get(0).getText();
            if (msgText == null || !msgText.contains(DocumentMessageBoxMessage.RELEASE_AFTER_SAVE_CONFIRMATION.getText())) {
                return; // not this dialog — role-dependent, may simply not appear at all
            }
            System.out.println("Release-after-save confirmation detected (role-dependent) — clicking Yes "
                    + "to release");
            final String priorText = msgText;
            SeleniumUtils.click(CommonButton.YES.locator(), WaitType.CLICKABLE, "RELEASE_AFTER_SAVE_YES");
            WaitUtils.fluentWaitBuilder()
                    .withMessage("the message box to change from the release-confirmation dialog to the next one")
                    .until(d -> {
                        List<WebElement> els = d.findElements(Loc.MESSAGE_TEXT);
                        if (els.isEmpty() || !els.get(0).isDisplayed()) return false;
                        String now = els.get(0).getText();
                        return now != null && !now.isBlank() && !now.equals(priorText);
                    });
            System.out.println("Message box changed after accepting release.");
        } catch (Exception e) {
            System.out.println("Release-after-save confirmation check skipped (non-fatal): " + e.getMessage());
        }
    }

    private void handleTdsAutoJournalPromptIfPresent(WebDriverWait wait, WebDriver driver) {
        try {
            List<WebElement> continueBtns = driver.findElements(CommonButton.CONTINUE.locator());
            if (!continueBtns.isEmpty() && continueBtns.get(0).isDisplayed()) {
                System.out.println("TDS Auto Journal prompt detected — clicking Continue");
                SeleniumUtils.click(CommonButton.CONTINUE.locator(), WaitType.CLICKABLE, "TDS_AUTO_JOURNAL_CONTINUE");
                // Wait for ExtJS to swap dialog text from TDS prompt to the document-save success message.
                // The same messagebox-1001-msg element is reused — it never goes invisible — so waiting for
                // invisibility would burn the full timeout. Waiting for the text to change is immediate.
                WaitUtils.fluentWaitBuilder().until(ExpectedConditions.not(
                        ExpectedConditions.textToBePresentInElementLocated(
                                Loc.MESSAGE_TEXT, "Auto TDS Journal")));
                System.out.println("Success messagebox is now visible after TDS Auto Journal Continue.");
            }
        } catch (Exception e) {
            System.out.println("TDS Auto Journal Continue check skipped (non-fatal): " + e.getMessage());
        }
    }
}
