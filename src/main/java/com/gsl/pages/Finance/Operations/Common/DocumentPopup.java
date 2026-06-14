package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.enums.CommonButton;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
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

        System.out.println("Waiting for payment-info-check warning dialog (" + warning + ")...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(warning.proceedLocator()));
        System.out.println("Payment info warning dialog confirmed visible for " + warning);

        // Non-blocking message-text read for diagnostics — same AOP caveat as before.
        try {
            By msgLocator = By.xpath(
                    "//span[normalize-space()='" + warning.getMessageSubstring().replace("'", "\\'") + "']"
                    + "/ancestor::div[contains(@class,'x-message-box')]"
                    + "//div[contains(@class,'x-component-default')]");
            int maxRetries = ConfigFactory.getWaitConfig().warningMsgMaxRetries();
            WebElement msgEl = null;
            for (int attempt = 0; attempt < maxRetries && msgEl == null; attempt++) {
                try {
                    msgEl = WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().shortWaitSeconds())
                            .until(ExpectedConditions.visibilityOfElementLocated(msgLocator));
                } catch (Exception ignored) {}
            }
            if (msgEl != null)
                System.out.println("Warning popup message: " + msgEl.getAttribute("innerText"));
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
    public String verifyErrorMessage(String expectedMessage) {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = WaitUtils.webDriverWait();

        System.out.println("Waiting for validation error popup to appear...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));
        String actualMessage = driver.findElement(Loc.MESSAGE_TEXT).getText();
        System.out.println("Validation error popup message: " + actualMessage);

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
        WaitUtils.webDriverWait().until(
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
        wait.until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));
        System.out.println("Messagebox is now visible");

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
                wait.until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));
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

        wait.until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));
        System.out.println("Messagebox is now visible after payment info proceed.");

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
    private void handleTdsAutoJournalPromptIfPresent(WebDriverWait wait, WebDriver driver) {
        try {
            List<WebElement> continueBtns = driver.findElements(CommonButton.CONTINUE.locator());
            if (!continueBtns.isEmpty() && continueBtns.get(0).isDisplayed()) {
                System.out.println("TDS Auto Journal prompt detected — clicking Continue");
                SeleniumUtils.click(CommonButton.CONTINUE.locator(), WaitType.CLICKABLE, "TDS_AUTO_JOURNAL_CONTINUE");
                // Wait for the TDS prompt to close, then wait for the success popup
                wait.until(ExpectedConditions.invisibilityOfElementLocated(Loc.MESSAGE_TEXT));
                wait.until(ExpectedConditions.visibilityOfElementLocated(Loc.MESSAGE_TEXT));
                System.out.println("Success messagebox is now visible after TDS Auto Journal Continue.");
            }
        } catch (Exception e) {
            System.out.println("TDS Auto Journal Continue check skipped (non-fatal): " + e.getMessage());
        }
    }
}
