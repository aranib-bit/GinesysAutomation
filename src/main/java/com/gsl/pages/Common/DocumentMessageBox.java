package com.gsl.pages.Common;

import com.gsl.enums.WaitType;
import com.gsl.pages.Inventory.Product_Definition.ItemListPage;
import com.gsl.pages.Procurement.Operations.ReceiptsListPage;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.testng.Assert;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentMessageBox {
    private static final By MESSAGE_TEXT       = By.xpath("//*[@id='messagebox-1001-msg']");
    private static final By HEADER_TEXT        = By.id("messagebox-1001_header-title-textEl");
    private static final By OK_BUTTON          = By.xpath("//span[text()='OK']");
    private static final By NO_BUTTON          = By.xpath("//span[text()='No']");
    private static final By CLOSE_BUTTON       = By.xpath("//*[@class='mdi mdi-24px mdi-close']");
    private static final By DISCARD_YES_BUTTON = By.xpath("//span[text()='Yes']");
    private static final By PROCEED_BUTTON     = By.xpath("//span[text()='Proceed']");
    private static final By LOADER             = By.xpath("//div[contains(@class,'x-mask-msg-text') and normalize-space()='Loading...']");

    // Regex pattern for extracting Document No
    private final Pattern documentPattern = Pattern.compile("\\b[A-Z]+/\\d+/\\d{2}-\\d{2}\\b");

    /**
     * Get the full message text from the popup
     */
    public String getMessageText() {
        System.out.println("Inside getMessageText");
        return SeleniumActions.getText(MESSAGE_TEXT);
    }

    /**
     * Get the header status text from the popup
     */
    public String getHeaderStatus() {
        return SeleniumActions.getText(HEADER_TEXT);
    }

    /**
     * Validate if the message status is success
     */
    public boolean isSuccess() {
        String header = getHeaderStatus();
        if (header != null) {
            header = header.trim();
        }
        return "success".equalsIgnoreCase(header);
    }

    /**
     * Extract document number from message using regex
     */
    public String extractDocumentNo() {
        String documentMessage = getMessageText();
        Matcher matcher = documentPattern.matcher(documentMessage);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * High-level verification method with TestNG assertions
     * Returns header + document number
     */
    public DocumentSaveResult verifyDocumentSave() {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        String status = getHeaderStatus();
        System.out.println("status is"+status );
        Assert.assertNotNull(status, "Header status was not found");
        Assert.assertTrue(isSuccess(), "Document save was not successful. Header: " + status);

        System.out.println("Document Save Status: " + status);

        String documentNo = extractDocumentNo();
        Assert.assertNotNull(documentNo,
                "Document number was not found. Message text: " + getMessageText());

        System.out.println("Extracted Document No: " + documentNo);
        ExtentLogger.pass("Extracted Document No: " + documentNo);
        return new DocumentSaveResult(status, documentNo);
    }

    public void clickOk() {
        SeleniumUtils.click(OK_BUTTON, WaitType.CLICKABLE, "OK_BUTTON_CONFIRM");
    }

    public void clickProceed() {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(PROCEED_BUTTON, WaitType.CLICKABLE, "PROCEED_BUTTON");
        SeleniumUtils.elementDisappear(LOADER, "LOADER");
    }

    // --- Non-Consignment Flow ---
    public ReceiptsListPage rejectPIAndClose(String vendorType) throws InterruptedException {
        if (vendorType.equalsIgnoreCase("Non-Consignment")) {
            System.out.println("Currently PI will not be generated, so clicking No...");
            SeleniumUtils.click(NO_BUTTON, WaitType.CLICKABLE, "NO_BUTTON");
            JavascriptExecutorUtils.waitForPageLoad();
            JavascriptExecutorUtils.waitForExtJS();

            SeleniumUtils.click(CLOSE_BUTTON, WaitType.CLICKABLE, "CLOSE_BUTTON");
            JavascriptExecutorUtils.waitForPageLoad();
            JavascriptExecutorUtils.waitForExtJS();

            SeleniumUtils.click(DISCARD_YES_BUTTON, WaitType.CLICKABLE, "DISCARD_YES_BUTTON");
            JavascriptExecutorUtils.waitForPageLoad();
            JavascriptExecutorUtils.waitForExtJS();

            JavascriptExecutorUtils.logLoadingComponents();
            JavascriptExecutorUtils.waitForComponentReady();

            return new ReceiptsListPage();
        } else {
            System.out.println("Vendor Type is not Non-Consignment, skipping PI rejection flow.");
        }
        return null;
    }

    // --- Convenience Method ---
    public void verifyAndClose() {
        DocumentSaveResult result = verifyDocumentSave();
        System.out.println("Verified Document Save → Status: " + result.getHeaderStatus()
                + ", DocNo: " + result.getDocumentNo());
        clickOk();
    }

    public void rejectPI() {
        System.out.println("Currently PI will not be generated, so clicking No...");
        SeleniumUtils.click(NO_BUTTON, WaitType.CLICKABLE, "NO_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
    }

    public void closeGRC() {
        SeleniumUtils.click(CLOSE_BUTTON, WaitType.CLICKABLE, "CLOSE_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();

        SeleniumUtils.click(DISCARD_YES_BUTTON, WaitType.CLICKABLE, "DISCARD_YES_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
    }

    public ItemListPage closeItem(){
        SeleniumUtils.click(DISCARD_YES_BUTTON, WaitType.CLICKABLE, "DISCARD_YES_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();
        return new ItemListPage();
    }

    public void handleSaveSuccess() {
        Assert.assertTrue(isSuccess(),
                "Document save was not successful. Header text: " + getHeaderStatus());

        String docNo = extractDocumentNo();
        Assert.assertNotNull(docNo,
                "Document number was not found. Message text: " + getMessageText());

        System.out.println("Extracted Document No: " + docNo);
        ExtentLogger.pass("Extracted Document No: " + docNo);
    }

    // ✅ New result class for returning header + docNo
    public static class DocumentSaveResult {
        private final String headerStatus;
        private final String documentNo;

        public DocumentSaveResult(String headerStatus, String documentNo) {
            this.headerStatus = headerStatus;
            this.documentNo = documentNo;
        }

        public String getHeaderStatus() {
            return headerStatus;
        }

        public String getDocumentNo() {
            return documentNo;
        }
    }
}
