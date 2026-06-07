package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.gsl.driver.DriverManager;
import com.gsl.enums.Finance.Operations.APVoucher.DeductTdsClearConfirmAction;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.PopupType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/**
 * Handles the "Calculated TDS and Payable amount will get cleared. Do you want to proceed?"
 * confirmation popup that appears in the AP Voucher sub-ledger grid.
 *
 * <p><b>Exact trigger condition:</b> Deduct TDS is changed from Yes → No and a TAB key
 * press fires the cell blur event. The popup is always expected on this path.
 *
 * <p>Package-private — used by {@link APVoucherSubLedgerInformation} only.
 */
final class TdsPayableClearConfirmHandler {

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
            DocumentMessageBoxMessage.TDS_PAYABLE_CLEAR_CONFIRMATION;

    private static final int MAX_RETRIES = 3;

    private TdsPayableClearConfirmHandler() {}

    /**
     * Waits for the TDS/Payable clear confirmation popup, retrying up to {@value MAX_RETRIES} times
     * (3 seconds per attempt). Validates header type and body text before clicking the action button.
     * Hard-fails after all retries are exhausted — the popup is required on the Yes→No path.
     *
     * @param confirmAction action from test data; {@code null} defaults to PROCEED
     * @param context       label for log/report messages (typically the sub-ledger name)
     */
    static void handle(DeductTdsClearConfirmAction confirmAction, String context) {
        ExtentLogger.info("[TDS_CLEAR_CONFIRM] Waiting for popup | locator=" + POPUP_LOCATOR);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(3))
                        .until(ExpectedConditions.visibilityOfElementLocated(POPUP_LOCATOR));

                // Popup found — validate and click
                validateAndClick(confirmAction, context);
                return;

            } catch (TimeoutException e) {
                if (attempt < MAX_RETRIES) {
                    Log.debug("[TDS_CLEAR_CONFIRM] attempt={} timeout, retrying", attempt);
                    ExtentLogger.info("[TDS_CLEAR_CONFIRM] Popup not detected — attempt "
                            + attempt + "/" + MAX_RETRIES + ", retrying... — " + context);
                } else {
                    String msg = "[TDS_CLEAR_CONFIRM] Popup not found after " + MAX_RETRIES
                            + " attempts — " + context;
                    ExtentLogger.fail(msg);
                    throw new AssertionError(msg);
                }
            }
        }
    }

    /**
     * Validates the popup header type and body text against the expected enum constant,
     * then clicks the action button. Hard-fails on any mismatch.
     */
    private static void validateAndClick(DeductTdsClearConfirmAction confirmAction, String context) {
        // 1. Validate header type via PopupType classifier
        String actualHeader = SeleniumUtils.getText(POPUP_HEADER, "TDS_CONFIRM_HEADER");
        PopupType actualType = PopupType.fromHeader(actualHeader);
        if (actualType != EXPECTED.getExpectedType()) {
            String msg = "[TDS_CLEAR_CONFIRM] Header mismatch: actual='" + actualHeader
                    + "' (type=" + actualType + ") expected type=" + EXPECTED.getExpectedType()
                    + " — " + context;
            ExtentLogger.fail(msg);
            throw new AssertionError(msg);
        }
        ExtentLogger.info("[TDS_CLEAR_CONFIRM] Header validated: '" + actualHeader + "' — " + context);

        // 2. Validate body text using full enum text — no hardcoding
        String actualBody = SeleniumUtils.getText(POPUP_BODY, "TDS_CONFIRM_BODY");
        if (actualBody == null || !actualBody.trim().contains(EXPECTED.getText())) {
            String msg = "[TDS_CLEAR_CONFIRM] Body mismatch: actual='" + actualBody
                    + "' expected='" + EXPECTED.getText() + "' — " + context;
            ExtentLogger.fail(msg);
            throw new AssertionError(msg);
        }
        ExtentLogger.info("[TDS_CLEAR_CONFIRM] Body validated — " + context);

        // 3. Click the action button
        DeductTdsClearConfirmAction action =
                (confirmAction != null) ? confirmAction : DeductTdsClearConfirmAction.PROCEED;
        if (action == DeductTdsClearConfirmAction.PROCEED) {
            SeleniumUtils.click(DocumentPopup.ButtonText.OK_GO_AHEAD.locator(),
                    WaitType.CLICKABLE, "TDS_CLEAR_CONFIRM_OK_GO_AHEAD");
            ExtentLogger.info("[TDS_CLEAR_CONFIRM] Ok, Go Ahead — " + context);
        } else {
            SeleniumUtils.click(DocumentPopup.ButtonText.CLOSE.locator(),
                    WaitType.CLICKABLE, "TDS_CLEAR_CONFIRM_CLOSE");
            ExtentLogger.info("[TDS_CLEAR_CONFIRM] Close — " + context);
        }
        JavascriptExecutorUtils.waitForExtJS();
    }
}
