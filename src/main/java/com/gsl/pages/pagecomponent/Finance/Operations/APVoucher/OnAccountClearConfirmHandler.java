package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.Finance.Operations.APVoucher.OnAccountClearConfirmAction;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.PopupType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Handles the "All Sub ledger, adjustments, TDS and payment details will get cleared.
 * Do you want to proceed?" warning popup in the AP Voucher document header.
 *
 * <p><b>Trigger condition:</b> On Account radio is toggled. Clicking "Close" causes the
 * app to revert On Account to its previous value; clicking "Ok, Go Ahead" proceeds and
 * clears sub-ledger/TDS/payment rows.
 *
 * <p>Package-private — used by {@link APVoucherDocumentInformation} only.
 */
final class OnAccountClearConfirmHandler {

    // role=alertdialog is only set on the actively displayed dialog — disambiguates from the
    // hidden x-message-box shells ExtJS keeps in the DOM (causes 2-match false positives otherwise)
    static final By POPUP_LOCATOR = By.xpath(
            "//div[@role='alertdialog'"
            + " and contains(@class,'x-message-box')"
            + " and not(contains(@class,'x-hide-display'))]");

    private static final By POPUP_HEADER = By.id("messagebox-1001_header-title-textEl");
    private static final By POPUP_BODY   = By.id("messagebox-1001-msg");

    // Single source of truth — all expected values (type + text) come from the enum constant
    private static final DocumentMessageBoxMessage EXPECTED =
            DocumentMessageBoxMessage.SUBLEDGER_CLEAR_ON_ACCOUNT_CHANGE;

    private OnAccountClearConfirmHandler() {}

    /**
     * Waits up to 3 seconds for the On Account clear warning popup to appear.
     * When the popup appears, validates the header type and body text against the
     * {@link DocumentMessageBoxMessage#SUBLEDGER_CLEAR_ON_ACCOUNT_CHANGE} enum constant before
     * clicking the action button. A mismatch on either header or body is a hard failure —
     * it means an unexpected popup appeared and further steps must not proceed.
     *
     * @param confirmAction action from test data; {@code null} defaults to OK_GO_AHEAD
     */
    static void handleIfPresent(OnAccountClearConfirmAction confirmAction) {
        ExtentLogger.info("[ON_ACCOUNT_CONFIRM] Waiting for popup | locator=" + POPUP_LOCATOR);
        try {
            WaitUtils.webDriverWait(ConfigFactory.getWaitConfig().shortWaitSeconds())
                    .until(ExpectedConditions.visibilityOfElementLocated(POPUP_LOCATOR));

            // 1. Validate header type via PopupType classifier
            String actualHeader = SeleniumUtils.getText(POPUP_HEADER, "ON_ACCOUNT_CONFIRM_HEADER");
            PopupType actualType = PopupType.fromHeader(actualHeader);
            if (actualType != EXPECTED.getExpectedType()) {
                String msg = "[ON_ACCOUNT_CONFIRM] Header mismatch: actual='" + actualHeader
                        + "' (type=" + actualType + ") expected type=" + EXPECTED.getExpectedType();
                ExtentLogger.fail(msg);
                throw new AssertionError(msg);
            }
            ExtentLogger.info("[ON_ACCOUNT_CONFIRM] Header validated: '" + actualHeader + "'");

            // 2. Validate body text using full enum text — no hardcoding
            String actualBody = SeleniumUtils.getText(POPUP_BODY, "ON_ACCOUNT_CONFIRM_BODY");
            if (actualBody == null || !actualBody.trim().contains(EXPECTED.getText())) {
                String msg = "[ON_ACCOUNT_CONFIRM] Body mismatch: actual='" + actualBody
                        + "' expected='" + EXPECTED.getText() + "'";
                ExtentLogger.fail(msg);
                throw new AssertionError(msg);
            }
            ExtentLogger.info("[ON_ACCOUNT_CONFIRM] Body validated");

            // 3. Click the action button
            OnAccountClearConfirmAction action =
                    (confirmAction != null) ? confirmAction : OnAccountClearConfirmAction.OK_GO_AHEAD;
            if (action == OnAccountClearConfirmAction.OK_GO_AHEAD) {
                SeleniumUtils.click(DocumentPopup.ButtonText.OK_GO_AHEAD_CAPS_START.locator(),
                        WaitType.CLICKABLE, "ON_ACCOUNT_CONFIRM_OK_GO_AHEAD");
                ExtentLogger.info("[ON_ACCOUNT_CONFIRM] Ok, Go Ahead — sub-ledger/TDS/payment rows cleared");
            } else {
                SeleniumUtils.click(DocumentPopup.ButtonText.CLOSE.locator(),
                        WaitType.CLICKABLE, "ON_ACCOUNT_CONFIRM_CLOSE");
                ExtentLogger.info("[ON_ACCOUNT_CONFIRM] Close — On Account reverted to previous value");
            }
            JavascriptExecutorUtils.waitForExtJS();

        } catch (TimeoutException e) {
            Log.debug("[ON_ACCOUNT_CONFIRM] Popup did not appear — no-op");
            ExtentLogger.info("[ON_ACCOUNT_CONFIRM] Popup not detected within 3s — no-op");
        }
    }
}
