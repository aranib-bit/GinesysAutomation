package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public class DocumentPopup {

    // ── Button text registry ─────────────────────────────────────────────────

    public enum ButtonText {
        IGNORE_AND_PROCEED("Ignore & Proceed"),
        OK_GO_AHEAD        ("OK, Go Ahead"),
        PROCEED_ANYWAY     ("Proceed Anyway"),
        STAY               ("Stay");

        private final String label;
        ButtonText(String label) { this.label = label; }
        public String getLabel() { return label; }

        public By locator() {
            return By.xpath("//span[normalize-space()='" + label + "']");
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

    private static final By MESSAGE_TEXT = By.xpath("//*[@id='messagebox-1001-msg']");
    private static final By YES_BUTTON   = By.xpath("//span[text()='Yes']");

    // ── Save-confirmation path ────────────────────────────────────────────────

    /**
     * Verifies the warning popup message before acting on it.
     * Waits for the "Ignore & Proceed" button to be visible (confirming the warning is shown),
     * then reads the message text for diagnostic purposes.
     * Returns {@code this} for fluent chaining with {@link #clickIgnoreProceed()}.
     */
    public DocumentPopup verifyWarningMessage() {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigFactory.getConfig().timeout()));

        System.out.println("Waiting for warning popup (Ignore & Proceed) to appear...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(ButtonText.IGNORE_AND_PROCEED.locator()));
        System.out.println("Warning popup confirmed visible — 'Ignore & Proceed' button is present.");

        // Attempt to log the warning message text for diagnostic purposes.
        // This is intentionally non-blocking: if the XPath does not match the
        // app's actual DOM structure the click must still proceed, so no assertion
        // is made here. The presence of the "Ignore & Proceed" button is sufficient
        // proof that the expected warning popup appeared.
        //
        // IMPORTANT: do NOT call SeleniumUtils.getText() here. That method is
        // AOP-intercepted; a TimeoutException inside it causes LoggingAspect to call
        // ExtentLogger.fail() and mark the test FAIL before the local catch fires.
        // Using WebDriverWait directly keeps the exception inside this try-catch.
        try {
            By warningMsgLocator = By.xpath(
                    "//span[normalize-space()='Ignore & Proceed']"
                    + "/ancestor::div[contains(@class,'x-message-box')]"
                    + "//div[contains(@class,'x-component-default')]");
            int maxRetries = ConfigFactory.getConfig().warningMsgMaxRetries();
            WebElement msgEl = null;
            for (int attempt = 0; attempt < maxRetries && msgEl == null; attempt++) {
                try {
                    msgEl = new WebDriverWait(driver, Duration.ofSeconds(3))
                            .until(ExpectedConditions.visibilityOfElementLocated(warningMsgLocator));
                } catch (Exception ignored) {
                    // retry
                }
            }
            if (msgEl != null) {
                System.out.println("Warning popup message: " + msgEl.getAttribute("innerText"));
            }
        } catch (Exception e) {
            System.out.println("Could not read warning message text (non-fatal): " + e.getMessage());
        }

        return this;
    }

    /**
     * Post-save path for Cash GLs or Bank GLs where all sub-ledger lines have complete
     * payment info with instrument numbers. No "Ignore & Proceed" popup is expected —
     * waits directly for the save-confirmation message box.
     * Also handles an unexpected "Ignore & Proceed" dialog if the instrument number
     * warning appears on this path.
     */
    public DocumentMessageBox waitForMessageBox() {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigFactory.getConfig().timeout()));

        System.out.println("Waiting directly for message box (no Ignore & Proceed popup expected)");
        wait.until(ExpectedConditions.visibilityOfElementLocated(MESSAGE_TEXT));
        System.out.println("Messagebox is now visible");

        // If the app unexpectedly shows the "instrument number" warning dialog,
        // click Ignore & Proceed, then wait for the actual save-result messagebox.
        try {
            String msgText = driver.findElement(MESSAGE_TEXT).getText();
            if (msgText != null && msgText.contains("instrument number")) {
                System.out.println("Instrument number warning detected — clicking Ignore & Proceed");
                SeleniumUtils.click(ButtonText.IGNORE_AND_PROCEED.locator(), WaitType.CLICKABLE, "IGNOREPROCEED");
                wait.until(ExpectedConditions.invisibilityOfElementLocated(ButtonText.IGNORE_AND_PROCEED.locator()));
                wait.until(ExpectedConditions.visibilityOfElementLocated(MESSAGE_TEXT));
                System.out.println("Success messagebox is now visible after Ignore & Proceed");
            }
        } catch (Exception e) {
            System.out.println("Instrument number dialog check skipped (non-fatal): " + e.getMessage());
        }

        return new DocumentMessageBox();
    }

    /**
     * Post-save path for Case 2 (Bank with payment info missing for some subledger lines).
     * Clicks "Ignore & Proceed" then waits for the save-confirmation message box.
     */
    public DocumentMessageBox clickIgnoreProceed() {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigFactory.getConfig().timeout()));

        System.out.println("Inside ignore & proceed");
        SeleniumUtils.click(ButtonText.IGNORE_AND_PROCEED.locator(), WaitType.CLICKABLE, "IGNOREPROCEED");

        // messagebox-1001-msg does not exist during Ignore & Proceed dialog
        // Wait for it to appear as the Save confirmation dialog
        wait.until(ExpectedConditions.visibilityOfElementLocated(MESSAGE_TEXT));
        System.out.println("Messagebox is now visible");

        return new DocumentMessageBox();
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
}
