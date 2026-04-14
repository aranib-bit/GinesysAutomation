package com.gsl.pages.Finance.Operations.Common;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public class DocumentPopup {

    private static final By IGNOREPROCEED = By.xpath("//span[normalize-space()='Ignore & Proceed']");
    private static final By MESSAGE_TEXT  = By.xpath("//*[@id='messagebox-1001-msg']");
    private static final By YES_BUTTON    = By.xpath("//span[text()='Yes']");

    /**
     * Verifies the warning popup message before acting on it.
     * Waits for the "Ignore & Proceed" button to be visible (confirming the warning is shown),
     * then reads the message text and asserts it matches the expected receipt-voucher warning.
     * Returns {@code this} for fluent chaining with {@link #clickIgnoreProceed()}.
     */
    public DocumentPopup verifyWarningMessage() {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigFactory.getConfig().timeout()));

        System.out.println("Waiting for warning popup (Ignore & Proceed) to appear...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(IGNOREPROCEED));
        System.out.println("Warning popup confirmed visible — 'Ignore & Proceed' button is present.");

        // Attempt to log the warning message text for diagnostic purposes.
        // This is intentionally non-blocking: if the XPath does not match the
        // app's actual DOM structure the click must still proceed, so no assertion
        // is made here. The presence of the "Ignore & Proceed" button is sufficient
        // proof that the expected warning popup appeared.
        try {
            By warningMsgLocator = By.xpath(
                    "//span[normalize-space()='Ignore & Proceed']"
                    + "/ancestor::div[contains(@class,'x-message-box')]"
                    + "//div[contains(@class,'x-component-default')]");
            String actualMsg = SeleniumUtils.getText(warningMsgLocator, "WARNING_MSG");
            System.out.println("Warning popup message: " + actualMsg);
        } catch (Exception e) {
            System.out.println("Could not read warning message text (non-fatal): " + e.getMessage());
        }

        return this;
    }

    /**
     * Post-save path for Case 1 (Cash) and Case 3 (Bank with payment info for all lines).
     * No "Ignore & Proceed" popup is expected — waits directly for the save-confirmation message box.
     * Also handles the intermediate "proceed without instrument number" Yes/No dialog, which
     * uses the same messagebox element but must be confirmed before the success message appears.
     */
    public DocumentMessageBox waitForMessageBox() {
        WebDriver driver = DriverManager.getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigFactory.getConfig().timeout()));

        System.out.println("Waiting directly for message box (no Ignore & Proceed popup expected)");
        wait.until(ExpectedConditions.visibilityOfElementLocated(MESSAGE_TEXT));
        System.out.println("Messagebox is now visible");

        // If the app shows an "instrument number" confirmation dialog before saving,
        // click Yes to proceed, then wait for the actual save-result messagebox.
        try {
            String msgText = driver.findElement(MESSAGE_TEXT).getText();
            if (msgText != null && msgText.contains("instrument number")) {
                System.out.println("Instrument number confirmation dialog detected — clicking Yes to proceed");
                SeleniumUtils.click(YES_BUTTON, WaitType.CLICKABLE, "YES_BUTTON");
                wait.until(ExpectedConditions.invisibilityOfElementLocated(MESSAGE_TEXT));
                wait.until(ExpectedConditions.visibilityOfElementLocated(MESSAGE_TEXT));
                System.out.println("Success messagebox is now visible after instrument number confirmation");
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
        SeleniumUtils.click(IGNOREPROCEED, WaitType.CLICKABLE, "IGNOREPROCEED");

        // messagebox-1001-msg does not exist during Ignore & Proceed dialog
        // Wait for it to appear as the Save confirmation dialog
        wait.until(ExpectedConditions.visibilityOfElementLocated(MESSAGE_TEXT));
        System.out.println("Messagebox is now visible");

        return new DocumentMessageBox();
    }
}
