package com.gsl.pages.Common;

import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.FinanceSaveHeaderType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Setup.AccountClassListPage;
import com.gsl.pages.Inventory.Product_Definition.ItemListPage;
import com.gsl.pages.Procurement.Operations.ReceiptsListPage;
import com.gsl.pages.enums.CommonButton;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.asserts.SoftAssert;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentMessageBox {

    // Non-button locators — unique to this class
    private static final class Loc {
        static final By MESSAGE_TEXT = By.xpath("//*[@id='messagebox-1001-msg']");
        static final By HEADER_TEXT  = By.id("messagebox-1001_header-title-textEl");
        static final By CLOSE_BUTTON = By.xpath("//*[@class='mdi mdi-24px mdi-close']");
        static final By LOADER       = By.xpath("//div[contains(@class,'x-mask-msg-text') and normalize-space()='Loading...']");
    }

    // Button locators → CommonButton enum (OK, No, Yes, Proceed, Continue)

    // Regex pattern for extracting Document No
    private final Pattern documentPattern = Pattern.compile("\\b[A-Z]+/\\d+/\\d{2}-\\d{2}\\b");

    /**
     * Get the full message text from the popup
     */
    public String getMessageText() {
        System.out.println("Inside getMessageText");
        return SeleniumActions.getText(Loc.MESSAGE_TEXT);
    }

    /**
     * Get the header status text from the popup
     */
    public String getHeaderStatus() {
        return SeleniumActions.getText(Loc.HEADER_TEXT);
    }

    /**
     * Validate if the message status is success. Accepts any header listed in
     * {@link FinanceSaveHeaderType} (e.g., "Success", "Result"). The Ginesys
     * voucher save popup returns "Result" with a "Created Successfully" body for
     * AR/AP vouchers — we accept that as success.
     */
    public boolean isSuccess() {
        return FinanceSaveHeaderType.isAcceptable(getHeaderStatus());
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
     * High-level verification with hard TestNG assertions. Use when the caller does not
     * own a {@link SoftAssert} (e.g., non-finance flows). Header check is hard; doc-number
     * check is hard.
     */
    public DocumentSaveResult verifyDocumentSave() {
        JavascriptExecutorUtils.waitForPageLoad();
        com.gsl.utils.Selenium.WaitUtils.waitForPostSaveMask("post-save-document-message-box");
        String status = getHeaderStatus();
        String messageText = getMessageText();
        Assert.assertNotNull(status, "Header status was not found");
        Assert.assertTrue(isSuccess(),
                "Document save was not successful. Header: " + status + " | Message: " + messageText);

        String documentNo = extractDocumentNo();
        Assert.assertNotNull(documentNo,
                "Document number was not found. Message text: " + messageText);

        ExtentLogger.infoHtml(buildVerifySaveHtml(documentNo, status, messageText));
        return new DocumentSaveResult(status, documentNo);
    }

    /**
     * High-level verification for AR/AP voucher flows. The header check is recorded on the
     * supplied {@link SoftAssert} so UI + DB validation can still run when the header is an
     * unexpected variant. The document-number extraction is the strongest signal that the
     * server actually persisted the voucher, so it remains a hard assertion — if no
     * document number is found, there is no point continuing.
     *
     * <p>The caller is responsible for invoking {@code softAssert.assertAll()} at the end of
     * the test so any deferred header mismatch surfaces as the final failure reason.</p>
     */
    public DocumentSaveResult verifyDocumentSave(SoftAssert softAssert) {
        JavascriptExecutorUtils.waitForPageLoad();
        com.gsl.utils.Selenium.WaitUtils.waitForPostSaveMask("post-save-document-message-box-soft");
        String status = getHeaderStatus();
        String messageText = getMessageText();

        if (status == null) {
            ExtentLogger.warning("Header status was not found on save popup — recorded on soft assert");
            softAssert.assertNotNull(status, "Header status was not found on save popup");
        } else if (!isSuccess()) {
            ExtentLogger.warning("Save header '" + status + "' is not in FinanceSaveHeaderType. "
                    + "Message: " + messageText + " — continuing with UI + DB validation; "
                    + "soft-assert will fail at end of test.");
            softAssert.fail("Unexpected save header '" + status + "'. Message: " + messageText
                    + ". Update FinanceSaveHeaderType if this is a new acceptable variant.");
        }

        String documentNo = extractDocumentNo();
        Assert.assertNotNull(documentNo,
                "Document number was not found. Message text: " + messageText);

        ExtentLogger.infoHtml(buildVerifySaveHtml(documentNo, status, messageText));
        return new DocumentSaveResult(status, documentNo);
    }

    private static String buildVerifySaveHtml(String documentNo, String status, String messageText) {
        return "<div style='font-family:monospace;font-size:13px;line-height:2;padding:10px 12px;"
            + "background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;'>"
            + "<b style='font-size:14px;'>VERIFY SAVE RESULT</b><br/>"
            + "&#10003;&nbsp; Voucher created shown in UI &mdash; Document No: <b>"
            + esc(documentNo) + "</b><br/>"
            + "&#10003;&nbsp; Save status: " + esc(status) + "<br/>"
            + "&#10003;&nbsp; Save message: " + esc(messageText) + "<br/>"
            + "</div>";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void clickOk() {
        SeleniumUtils.click(CommonButton.OK.locator(), WaitType.CLICKABLE, "OK_BUTTON_CONFIRM");
        ExtentLogger.info("Save dialog dismissed");
    }

    public void clickProceed() {
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(CommonButton.PROCEED.locator(), WaitType.CLICKABLE, "PROCEED_BUTTON");
        SeleniumUtils.elementDisappear(Loc.LOADER, "LOADER");
    }

    // --- Non-Consignment Flow ---
    public ReceiptsListPage rejectPIAndClose(String vendorType) throws InterruptedException {
        if (vendorType.equalsIgnoreCase("Non-Consignment")) {
            System.out.println("Currently PI will not be generated, so clicking No...");
            SeleniumUtils.click(CommonButton.NO.locator(), WaitType.CLICKABLE, "NO_BUTTON");
            JavascriptExecutorUtils.waitForPageLoad();

            SeleniumUtils.click(Loc.CLOSE_BUTTON, WaitType.CLICKABLE, "CLOSE_BUTTON");
            JavascriptExecutorUtils.waitForPageLoad();

            SeleniumUtils.click(CommonButton.YES.locator(), WaitType.CLICKABLE, "DISCARD_YES_BUTTON");
            JavascriptExecutorUtils.waitForPageLoad();

            JavascriptExecutorUtils.logLoadingComponents();
            com.gsl.utils.Selenium.WaitUtils.waitForExtMaskBestEffort("post-yes-receipts-close");

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
        SeleniumUtils.click(CommonButton.NO.locator(), WaitType.CLICKABLE, "NO_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
    }

    public void clickNo() {
        SeleniumUtils.click(CommonButton.NO.locator(), WaitType.CLICKABLE, "NO_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
    }

    public void clickContinue() {
        SeleniumUtils.click(CommonButton.CONTINUE.locator(), WaitType.CLICKABLE, "CONTINUE_BUTTON");
        // Wait for the TDS prompt to dismiss before the caller waits for the next popup
        SeleniumUtils.elementDisappear(Loc.MESSAGE_TEXT, "TDS_PROMPT_MESSAGEBOX");
    }

    public void verifyMessage(DocumentMessageBoxMessage expected) {
        String actual = getMessageText(); // delegates to Loc.MESSAGE_TEXT via getMessageText()
        Assert.assertTrue(actual.contains(expected.getText()),
                "Expected message box to contain: [" + expected.getText() + "] but got: [" + actual + "]");
    }

    public void closeGRC() {
        SeleniumUtils.click(Loc.CLOSE_BUTTON, WaitType.CLICKABLE, "CLOSE_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();

        SeleniumUtils.click(CommonButton.YES.locator(), WaitType.CLICKABLE, "DISCARD_YES_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
    }

    /**
     * Clicks "Yes" on the discard-confirmation dialog and returns to the list page — the caller
     * supplies which domain's list page to construct (e.g. {@code ArVoucherListPage::new}),
     * since this dialog is shared across AR/AP/GV and previously always hard-constructed an
     * {@code ArVoucherListPage} regardless of the actual calling domain.
     */
    public <T> T clickYes(java.util.function.Supplier<T> listPageFactory) {
        SeleniumUtils.click(CommonButton.YES.locator(), WaitType.CLICKABLE, "DISCARD_YES_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
        com.gsl.utils.Selenium.WaitUtils.waitForExtMaskBestEffort("post-yes-close");
        return listPageFactory.get();
    }

    public ItemListPage closeItem(){
        SeleniumUtils.click(CommonButton.YES.locator(), WaitType.CLICKABLE, "DISCARD_YES_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
        return new ItemListPage();
    }

    public AccountClassListPage closeAccountClass() {
        SeleniumUtils.click(CommonButton.YES.locator(), WaitType.CLICKABLE, "DISCARD_YES_BUTTON");
        JavascriptExecutorUtils.waitForPageLoad();
        return new AccountClassListPage();
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
