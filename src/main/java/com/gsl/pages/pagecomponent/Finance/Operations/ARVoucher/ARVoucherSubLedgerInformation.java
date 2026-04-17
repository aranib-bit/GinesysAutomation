package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.driver.DriverManager;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.gsl.utils.Selenium.ExtJsUtils.setExtJsComboByName;

public class ARVoucherSubLedgerInformation {
    private static final By SUBLEDGERNAMEBLANK=By.xpath("(//div[contains(@class,'x-window')]//table[contains(@class,'x-grid-item')])[last()]//tr[last()]//td[3]");
    private static final String SUBLEDGERSELECTION_XPATH="//span[text()='%s']";
    private static final By SUBLEDGERAMOUNTCR = By.xpath("//tr[.//td/div[normalize-space()='ABUL KASHEM MOLLHA'] and .//td/div[normalize-space()='Hena Embroidery']]//td[contains(@data-columnid,'numbercolumn')]//div");
    private static final String SUBLEDGERADJUSTBUTTON_TEMPLATE =
            "//tr[" +
            "    td[@data-columnid=//span[normalize-space()='Sub Ledger']/ancestor::div[contains(@id,'gridcolumn')]/@id]" +
            "    /div[normalize-space()='%s']" +
            "    and" +
            "    td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]" +
            "    /div[normalize-space()='%s']" +
            "]" +
            "//td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]" +
            "/following-sibling::td" +
            "/div//button[normalize-space()='Adjust']";
    private static final String PAYMENT_BUTTON_TEMPLATE =
            "//tr[" +
            "    td[@data-columnid=//span[normalize-space()='Sub Ledger']/ancestor::div[contains(@id,'gridcolumn')]/@id]" +
            "    /div[normalize-space()='%s']" +
            "    and" +
            "    td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]" +
            "    /div[normalize-space()='%s']" +
            "]" +
            "//td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]" +
            "/following-sibling::td" +
            "/div//button[normalize-space()='Cheque/RTGS']";
    // ── Active ExtJS editor – works regardless of row content ────────────────
    private static final By ACTIVE_EDITOR_INPUT = By.xpath(
            "//div[contains(@class,'x-editor') " +
                    "and not(contains(@class,'x-hide-display'))]//input"
    );

    public ARVoucherSubLedgerInformation setSubLedgerName1(String subledgerName) {
        // 🔑 USE STRING TEMPLATE HERE
        SeleniumUtils.click(
                SUBLEDGERNAMEBLANK,
                WaitType.CLICKABLE,
                "SUBLEDGER NAME BLANK"
        );
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        System.out.println("After Sub Ledger Blank Row Click");

        setExtJsComboByName("subLedgerName", subledgerName);



        //SeleniumUtils.click(SUBLEDGERNAMEBLANK,WaitType.CLICKABLE,"SUBLEDGERNAMEBLANK");
        //SeleniumUtils.sendKeys(SUBLEDGERNAMEBLANK,WaitType.CLICKABLE,subledgerName,"SUBLEDGERNAMEBLANK");
        System.out.println("After Sub Ledger Set Value");
        //SeleniumUtils.click(getSubLedgerNameSelection(subledgerName), WaitType.CLICKABLE, "SUBLEDGERSELECTION_XPATH");
        return this;
    }

    public ARVoucherSubLedgerInformation setSubLedgerName(String subledgerName, String id, String referenceSite) {
        WebDriver driver = DriverManager.getDriver();
        System.out.println("=== STEP 1 : START Set Sub Ledger for Row 0 ===");
        JavascriptExecutorUtils.setSubLedger(driver, subledgerName, id, referenceSite);
        System.out.println("=== STEP 2 : setSubLedger completed ===");
        return this;
    }

    public ARVoucherSubLedgerInformation setSubLedgerAmountold(String subLedgerAmount) {
        System.out.println("Before double click");
        ActionUtils.doubleClick(SUBLEDGERAMOUNTCR);
        //SeleniumUtils.click(SUBLEDGERAMOUNTCR,WaitType.CLICKABLE,"SUBLEDGERAMOUNTCR");
        System.out.println("After double click");
        //System.out.println("Before clear");
        //SeleniumUtils.clear(SUBLEDGERAMOUNTCR,"SUBLEDGERAMOUNTCR");
        SeleniumUtils.sendKeys(SUBLEDGERAMOUNTCR,subLedgerAmount,"SUBLEDGERAMOUNTCR");
        return this;

    }


    public ARVoucherSubLedgerInformation setSubLedgerAmountold2(String subLedgerAmount) throws InterruptedException {

        WebDriver driver = DriverManager.getDriver();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        Thread.sleep(500);

        String result = (String) js.executeScript(
                "try {" +
                        "    var amount = parseFloat(arguments[0]);" +
                        "    var grids = Ext.ComponentQuery.query('grid');" +
                        "    for (var i = 0; i < grids.length; i++) {" +
                        "        var store = grids[i].getStore();" +
                        "        if (store && store.getCount() > 0) {" +
                        "            var rec = store.getAt(0);" +
                        "            rec.set('amount', amount);" +
                        // DO NOT call rec.commit() — keeps record dirty=true
                        // so ExtJS includes it in the save payload
                        "            return 'SUCCESS: amount=' + rec.get('amount') +" +
                        "                   ' dirty=' + rec.dirty;" +
                        "        }" +
                        "    }" +
                        "    return 'ERROR: No grid with records found';" +
                        "} catch(e) {" +
                        "    return 'ERROR: ' + e.message;" +
                        "}",
                subLedgerAmount
        );

        System.out.println("setSubLedgerAmount result: " + result);
        Thread.sleep(300);
        return this;
    }

    public ARVoucherSubLedgerInformation setSubLedgerAmount2(String subLedgerAmount) throws InterruptedException {

        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();

        Thread.sleep(500);

        String result = (String) js.executeScript(
                "try {" +
                        "    var amount = parseFloat(arguments[0]);" +

                        // Target the SPECIFIC sub ledger grid — same one used in setSubLedger
                        "    var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];" +
                        "    if (!childGrid) return 'ERROR: arvch_grid not found';" +

                        "    var store = childGrid.getStore();" +
                        "    if (!store) return 'ERROR: store not found';" +
                        "    if (store.getCount() === 0) return 'ERROR: store is empty';" +

                        "    var rec = store.getAt(0);" +
                        "    rec.set('amount', amount);" +

                        // Verify it is dirty — must be true for ExtJS to include in save payload
                        "    return 'SUCCESS: amount=' + rec.get('amount') + ' dirty=' + rec.dirty;" +
                        "} catch(e) {" +
                        "    return 'ERROR: ' + e.message;" +
                        "}",
                subLedgerAmount
        );

        System.out.println("setSubLedgerAmount result: " + result);
        Thread.sleep(300);
        return this;
    }

    public ARVoucherSubLedgerInformation setSubLedgerAmount(String subLedgerAmount) throws InterruptedException {

        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        Thread.sleep(500);

        String result = (String) js.executeScript(
                "try {" +
                        "    var amount = parseFloat(arguments[0]);" +
                        "    var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];" +
                        "    if (!childGrid) return 'ERROR: arvch_grid not found';" +
                        "    var store = childGrid.getStore();" +
                        "    if (!store || store.getCount() === 0) return 'ERROR: arvch_grid store empty';" +
                        "    var rec = store.getAt(0);" +
                        "    rec.set('amount', amount);" +
                        "    return 'SUCCESS: amount=' + rec.get('amount') + ' dirty=' + rec.dirty;" +
                        "} catch(e) {" +
                        "    return 'ERROR: ' + e.message;" +
                        "}",
                subLedgerAmount
        );

        System.out.println("setSubLedgerAmount result: " + result);
        Thread.sleep(300);
        return this;
    }




    /**
     * Adds a new empty row to the subledger grid.
     * Must be called before setSubLedgerName/setSubLedgerAmount for row index > 0.
     */
    public ARVoucherSubLedgerInformation addSubLedgerRow() {
        JavascriptExecutorUtils.addSubLedgerRow(DriverManager.getDriver());
        return this;
    }

    /**
     * Sets the subledger name on the specified row index.
     * Use row index 0 for the first row; call addSubLedgerRow() before each subsequent row.
     */
    public ARVoucherSubLedgerInformation setSubLedgerName(String subledgerName, String id, String referenceSite, int rowIndex) {
        if (rowIndex == 0) {
            JavascriptExecutorUtils.setSubLedger(DriverManager.getDriver(), subledgerName, id, referenceSite);
        } else {
            JavascriptExecutorUtils.setSubLedgerForRow(DriverManager.getDriver(), rowIndex, subledgerName, id, referenceSite);
        }
        return this;
    }

    public ARVoucherSubLedgerInformation setSubLedgerReferenceSite(String referenceSite, int rowIndex) {
        JavascriptExecutorUtils.setSubLedgerReferenceSite(DriverManager.getDriver(), rowIndex, referenceSite);
        return this;
    }

    public String getSubLedgerReferenceSite(int rowIndex) {
        return JavascriptExecutorUtils.getSubLedgerReferenceSiteName(DriverManager.getDriver(), rowIndex);
    }

    /**
     * Sets the subledger amount on the specified row index.
     */
    public ARVoucherSubLedgerInformation setSubLedgerAmount(String subLedgerAmount, int rowIndex) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        Thread.sleep(500);

        String result = (String) js.executeScript(
                "try {" +
                        "    var amount = parseFloat(arguments[0]);" +
                        "    var rowIndex = parseInt(arguments[1]);" +
                        "    var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];" +
                        "    if (!childGrid) return 'ERROR: arvch_grid not found';" +
                        "    var store = childGrid.getStore();" +
                        "    if (!store || store.getCount() === 0) return 'ERROR: arvch_grid store empty';" +
                        "    var rec = store.getAt(rowIndex);" +
                        "    if (!rec) return 'ERROR: row ' + rowIndex + ' not found';" +
                        "    rec.set('amount', amount);" +
                        "    return 'SUCCESS: row=' + rowIndex + ' amount=' + rec.get('amount') + ' dirty=' + rec.dirty;" +
                        "} catch(e) {" +
                        "    return 'ERROR: ' + e.message;" +
                        "}",
                subLedgerAmount, rowIndex
        );

        System.out.println("setSubLedgerAmount[" + rowIndex + "] result: " + result);
        Thread.sleep(300);
        return this;
    }

    public void clickAdjust(String subLedgerName, String referenceSite) {
        By adjustButton = By.xpath(String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE, subLedgerName, referenceSite));
        SeleniumUtils.click(adjustButton, WaitType.CLICKABLE, "SUBLEDGERADJUSTBUTTON");
    }


    private By getSubLedgerNameSelection(String subLedgerName) {
        String xpath = String.format(SUBLEDGERSELECTION_XPATH, subLedgerName);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    private PaymentInformationPopup clickPaymentButton(String subLedger, String referenceSite) {
        By button = By.xpath(String.format(PAYMENT_BUTTON_TEMPLATE, subLedger, referenceSite));
        SeleniumUtils.click(button, WaitType.CLICKABLE, "PAYMENT_BUTTON_" + subLedger);
        JavascriptExecutorUtils.waitForExtJS();
        return new PaymentInformationPopup();
    }

    public void handlePaymentInformation(
            List<SubLedgerInformation> subLedgerList,
            List<PaymentInformation> paymentList) {

        for (SubLedgerInformation subLedger : subLedgerList) {
            String lineNo = subLedger.getLineNo();
            if (lineNo == null || lineNo.isBlank()) continue;

            // Match by lineNo; also filter by testCaseID when both sides have it (Excel mode).
            // In DB mode sub-ledger testCaseID is null, so match by lineNo alone.
            PaymentInformation payment = paymentList.stream()
                    .filter(p -> p.getLineNo() != null
                            && p.getLineNo().equalsIgnoreCase(lineNo)
                            && (subLedger.getTestCaseID() == null || p.getTestCaseID() == null
                                || p.getTestCaseID().equalsIgnoreCase(subLedger.getTestCaseID())))
                    .findFirst()
                    .orElse(null);

            if (payment == null) continue;

            // Prefer paymentSelection set on the sub-ledger row (Excel mode, via @Column).
            // Fall back to paymentMode from the PaymentInformation row (DB mode, where
            // sub-ledger rows are built programmatically with paymentSelection=null).
            PaymentSelectionType effectiveType = subLedger.getPaymentSelection() != null
                    ? subLedger.getPaymentSelection()
                    : payment.getPaymentMode();

            if (effectiveType == null) continue;
            // Cash GL shows a validation message when the button is clicked — no popup to fill
            if (effectiveType == PaymentSelectionType.CASH) continue;

            String refSite = subLedger.getReferenceSite() != null ? subLedger.getReferenceSite() : "";
            clickPaymentButton(subLedger.getSubLedger(), refSite)
                    .fillAndSubmit(payment);

            JavascriptExecutorUtils.waitForExtJS();
        }
    }

}
