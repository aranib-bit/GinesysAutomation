package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Common.AdjustmentEntryParent;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.gsl.utils.Selenium.ExtJsUtils.setExtJsComboByName;

public class ARVoucherSubLedgerInformation implements AdjustmentEntryParent {
    private static final By SUBLEDGERNAMEBLANK=By.xpath("(//div[contains(@class,'x-window')]//table[contains(@class,'x-grid-item')])[last()]//tr[last()]//td[3]");
    private static final String SUBLEDGERSELECTION_XPATH="//span[text()='%s']";
    private static final By SUBLEDGERAMOUNTCR = By.xpath("//tr[.//td/div[normalize-space()='ABUL KASHEM MOLLHA'] and .//td/div[normalize-space()='Hena Embroidery']]//td[contains(@data-columnid,'numbercolumn')]//div");
    private static final String SUBLEDGERADJUSTBUTTON_TEMPLATE =
            "//tr[td[@data-columnid=//span[normalize-space()='Sub Ledger']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[text()='%s'] and td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s']]//td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]/following-sibling::td/div//button[normalize-space()='Adjust']";
    private static final String SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID =
            "//tr[td[@data-columnid=//span[normalize-space()='Sub Ledger']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[text()='%s'] and td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s'] and td[@data-columnid=//span[normalize-space()='Sub Ledger Id']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s']]//td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]/following-sibling::td/div//button[normalize-space()='Adjust']";
    private static final String PAYMENT_BUTTON_TEMPLATE =
            "//tr[td[@data-columnid=//span[normalize-space()='Sub Ledger']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[text()='%s'] and td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s']]//td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]/following-sibling::td/div//button[normalize-space()='Cheque/RTGS']";
    private static final String PAYMENT_BUTTON_TEMPLATE_WITH_SLID =
            "//tr[td[@data-columnid=//span[normalize-space()='Sub Ledger']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[text()='%s'] and td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s'] and td[@data-columnid=//span[normalize-space()='Sub Ledger Id']/ancestor::div[contains(@id,'gridcolumn')]/@id]/div[normalize-space()='%s']]//td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]/following-sibling::td/div//button[normalize-space()='Cheque/RTGS']";
    // ── Active ExtJS editor – works regardless of row content ────────────────
    private static final By ACTIVE_EDITOR_INPUT = By.xpath(
            "//div[contains(@class,'x-editor') " +
                    "and not(contains(@class,'x-hide-display'))]//input"
    );

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fills all sub-ledger rows, processes adjustments, and handles payment —
     * the complete sub-ledger section in one call.
     *
     * @param data         full test data (carries subledger list, select docs, payment list)
     * @param includeRetry true  → run Pass 2 reference-site re-apply + row-0 retry + read-back
     *                     false → Pass 1 only (used for NO_ADJUSTMENT scenarios)
     */
    public ARVoucherSubLedgerInformation fillSubLedgerRows(
            MergedFinanceArVoucherData data, boolean includeRetry) {

        final List<SubLedgerInformation> sls = data.getSubLedgerInformationList();

        // ── Pass 1: set sub-ledger name and amount ────────────────────────────
        for (int i = 0; i < sls.size(); i++) {
            SubLedgerInformation sl = sls.get(i);
            if (i > 0) addSubLedgerRow();
            String rawSlName = sl.getSubLedger();
            if (rawSlName == null || rawSlName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "SubLedger name is null/empty at row index " + i
                    + " (TestCaseID='" + sl.getTestCaseID() + "', LineNo='" + sl.getLineNo() + "'). "
                    + "Check Excel 'SubLedgerInformation' sheet — column header must be exactly 'Sub Ledger'."
                );
            }
            String slName  = rawSlName.trim();
            String slId    = sl.getId()            != null ? sl.getId().trim()            : "";
            String refSite = sl.getReferenceSite() != null ? sl.getReferenceSite().trim() : "";
            try {
                setSubLedgerName(slName, slId, refSite, i);
                setSubLedgerAmount(sl.getAmountCr().toPlainString(), i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted setting sub-ledger amount on row " + i, e);
            }
            Log.debug("Row {} | SL={} | Amt={}", i, sl.getSubLedger(), sl.getAmountCr());
        }

        if (includeRetry) {
            // ── Pass 2: re-apply reference sites ─────────────────────────────
            for (int i = 0; i < sls.size(); i++) {
                String refSite = sls.get(i).getReferenceSite() != null
                        ? sls.get(i).getReferenceSite() : "";
                setSubLedgerReferenceSite(refSite, i);
            }
            // Row-0 retry: proxy is warm after rows 1+ are processed
            {
                String refSite = sls.get(0).getReferenceSite() != null
                        ? sls.get(0).getReferenceSite() : "";
                setSubLedgerReferenceSite(refSite, 0);
            }
            // Read back actual reference site values set by the UI
            for (int i = 0; i < sls.size(); i++) {
                String actual = getSubLedgerReferenceSite(i);
                if (actual != null && !actual.isEmpty()) sls.get(i).setReferenceSite(actual);
            }
        }

        // ── Adjustments ───────────────────────────────────────────────────────
        List<SubLedgerInformation> adjustRows = sls.stream()
                .filter(r -> r.getAdjustmentType() != null)
                .collect(Collectors.toList());

        for (SubLedgerInformation row : adjustRows) {
            String refSite = row.getReferenceSite() != null ? row.getReferenceSite() : "";
            String slId    = row.getId()            != null ? row.getId()            : "";
            AdjustmentEntry adjustmentEntry = openAdjustmentEntry(row.getSubLedger(), refSite, slId);
            AdjustmentType adjType = row.getAdjustmentType();
            boolean needsConfirmation = data.getGridRelation() == SelectDocumentVariant.GridRelation.GREATER
                    || data.getGridRelation() == SelectDocumentVariant.GridRelation.LESS;
            Log.debug("AdjType: {} | GridRelation: {} | needsConfirmation: {}", adjType, data.getGridRelation(), needsConfirmation);

            if (adjType == AdjustmentType.SELECT_DOCUMENT) {
                SelectDocumentPopup popup = adjustmentEntry.selectDocument();
                String lineNo = row.getLineNo();
                List<SelectDocument> docs = data.getSelectDocumentList().stream()
                        .filter(d -> lineNo != null && lineNo.equalsIgnoreCase(d.getLineNo()))
                        .collect(Collectors.toList());
                Log.debug("Filtered docs for LineNo {}: {}", lineNo, docs.size());
                for (SelectDocument doc : docs) {
                    Log.debug("Select Document: {}", doc.getDocumentNumber());
                    popup.selectDocument(doc.getDocumentNumber());
                }
                popup.clickSelect();
                for (SelectDocument doc : docs) {
                    BigDecimal excelAmount = doc.getAdjustmentAmount();
                    if (excelAmount == null || excelAmount.compareTo(BigDecimal.ZERO) == 0) continue;
                    // P1: Reject negative adjustment amounts before touching the UI
                    if (excelAmount.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException(
                            "Adjustment amount cannot be negative for document ["
                            + doc.getDocumentNumber() + "]. Got: " + excelAmount.toPlainString()
                            + ". Fix the test data (Excel/DB) for this document.");
                    }
                    String uiStr = adjustmentEntry.getAdjustedAmountForDocument(doc.getDocumentNumber());
                    BigDecimal uiAmount = BigDecimalUtils.safeParse(uiStr);
                    Log.debug("Doc {} — UI: {} | Excel: {}", doc.getDocumentNumber(), uiAmount, excelAmount);
                    if (excelAmount.compareTo(uiAmount) != 0) {
                        Log.debug("Mismatch. Updating to: {}", excelAmount.toPlainString());
                        adjustmentEntry.setAdjustedAmountForDocument(
                                doc.getDocumentNumber(), excelAmount.toPlainString());
                    }
                }
                if (needsConfirmation) {
                    adjustmentEntry.clickOKExpectingConfirmation().clickProceedAnyway();
                } else {
                    adjustmentEntry.clickOK();
                }

            } else if (adjType == AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF) {
                if (needsConfirmation) {
                    adjustmentEntry.referenceSiteWiseFIFO().showReferenceSiteSummary()
                            .clickOKExpectingConfirmation().clickProceedAnyway();
                } else {
                    adjustmentEntry.referenceSiteWiseFIFO().showReferenceSiteSummary().clickOK();
                }

            } else if (adjType == AdjustmentType.FIFO_SETOFF) {
                if (needsConfirmation) {
                    adjustmentEntry.fifoSetOff().clickOKExpectingConfirmation().clickProceedAnyway();
                } else {
                    adjustmentEntry.fifoSetOff().clickOK();
                }
            }
        }

        // ── Payment ───────────────────────────────────────────────────────────
        if (data.isPaymentProvided()) {
            handlePaymentInformation(sls, data.getPaymentInformationList());
        }

        return this;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Clicks the Adjust button for the given sub-ledger row, then creates and
     * opens the AdjustmentEntry dialog (previously split across ArVoucherAdd).
     */
    private AdjustmentEntry openAdjustmentEntry(String subLedgerName,
                                                 String referenceSite, String slid) {
        clickAdjust(subLedgerName, referenceSite, slid);
        AdjustmentEntry entry = new AdjustmentEntry(this);
        entry.openAdjustedDocumentMenu();
        return entry;
    }

    private void setSubLedgerName1(String subledgerName) {
        SeleniumUtils.click(
                SUBLEDGERNAMEBLANK,
                WaitType.CLICKABLE,
                "SUBLEDGER NAME BLANK"
        );
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        System.out.println("After Sub Ledger Blank Row Click");
        setExtJsComboByName("subLedgerName", subledgerName);
        System.out.println("After Sub Ledger Set Value");
    }

    private ARVoucherSubLedgerInformation setSubLedgerName(String subledgerName, String id, String referenceSite) {
        WebDriver driver = DriverManager.getDriver();
        System.out.println("=== STEP 1 : START Set Sub Ledger for Row 0 ===");
        JavascriptExecutorUtils.setSubLedger(driver, subledgerName, id, referenceSite);
        System.out.println("=== STEP 2 : setSubLedger completed ===");
        return this;
    }

    private ARVoucherSubLedgerInformation setSubLedgerAmountold(String subLedgerAmount) {
        System.out.println("Before double click");
        ActionUtils.doubleClick(SUBLEDGERAMOUNTCR);
        System.out.println("After double click");
        SeleniumUtils.sendKeys(SUBLEDGERAMOUNTCR,subLedgerAmount,"SUBLEDGERAMOUNTCR");
        return this;
    }

    private ARVoucherSubLedgerInformation setSubLedgerAmountold2(String subLedgerAmount) throws InterruptedException {
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

    private ARVoucherSubLedgerInformation setSubLedgerAmount2(String subLedgerAmount) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        Thread.sleep(500);
        String result = (String) js.executeScript(
                "try {" +
                        "    var amount = parseFloat(arguments[0]);" +
                        "    var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];" +
                        "    if (!childGrid) return 'ERROR: arvch_grid not found';" +
                        "    var store = childGrid.getStore();" +
                        "    if (!store) return 'ERROR: store not found';" +
                        "    if (store.getCount() === 0) return 'ERROR: store is empty';" +
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

    private ARVoucherSubLedgerInformation setSubLedgerAmount(String subLedgerAmount) throws InterruptedException {
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
     */
    private ARVoucherSubLedgerInformation addSubLedgerRow() {
        JavascriptExecutorUtils.addSubLedgerRow(DriverManager.getDriver());
        return this;
    }

    /**
     * Sets the subledger name on the specified row index.
     */
    private ARVoucherSubLedgerInformation setSubLedgerName(String subledgerName, String id, String referenceSite, int rowIndex) {
        if (rowIndex == 0) {
            JavascriptExecutorUtils.setSubLedger(DriverManager.getDriver(), subledgerName, id, referenceSite);
        } else {
            JavascriptExecutorUtils.setSubLedgerForRow(DriverManager.getDriver(), rowIndex, subledgerName, id, referenceSite);
        }
        return this;
    }

    private ARVoucherSubLedgerInformation setSubLedgerReferenceSite(String referenceSite, int rowIndex) {
        JavascriptExecutorUtils.setSubLedgerReferenceSite(DriverManager.getDriver(), rowIndex, referenceSite);
        return this;
    }

    private String getSubLedgerReferenceSite(int rowIndex) {
        return JavascriptExecutorUtils.getSubLedgerReferenceSiteName(DriverManager.getDriver(), rowIndex);
    }

    /**
     * Sets the subledger amount on the specified row index.
     */
    private ARVoucherSubLedgerInformation setSubLedgerAmount(String subLedgerAmount, int rowIndex) throws InterruptedException {
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

    private void clickAdjust(String subLedgerName, String referenceSite, String slid) {
        String xpath = (slid != null && !slid.isBlank())
                ? String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE_WITH_SLID, subLedgerName, referenceSite, slid)
                : String.format(SUBLEDGERADJUSTBUTTON_TEMPLATE, subLedgerName, referenceSite);
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "SUBLEDGERADJUSTBUTTON");
    }

    private By getSubLedgerNameSelection(String subLedgerName) {
        String xpath = String.format(SUBLEDGERSELECTION_XPATH, subLedgerName);
        System.out.println("Generated XPath: " + xpath);
        return By.xpath(xpath);
    }

    private PaymentInformationPopup clickPaymentButton(String subLedger, String referenceSite, String slid) {
        String xpath = (slid != null && !slid.isBlank())
                ? String.format(PAYMENT_BUTTON_TEMPLATE_WITH_SLID, subLedger, referenceSite, slid)
                : String.format(PAYMENT_BUTTON_TEMPLATE, subLedger, referenceSite);
        SeleniumUtils.click(By.xpath(xpath), WaitType.CLICKABLE, "PAYMENT_BUTTON_" + subLedger);
        JavascriptExecutorUtils.waitForExtJS();
        return new PaymentInformationPopup();
    }

    private void handlePaymentInformation(
            List<SubLedgerInformation> subLedgerList,
            List<PaymentInformation> paymentList) {

        for (SubLedgerInformation subLedger : subLedgerList) {
            String lineNo = subLedger.getLineNo();
            if (lineNo == null || lineNo.isBlank()) continue;

            PaymentInformation payment = paymentList.stream()
                    .filter(p -> p.getLineNo() != null
                            && p.getLineNo().equalsIgnoreCase(lineNo)
                            && (subLedger.getTestCaseID() == null || p.getTestCaseID() == null
                                || p.getTestCaseID().equalsIgnoreCase(subLedger.getTestCaseID())))
                    .findFirst()
                    .orElse(null);

            if (payment == null) continue;

            PaymentSelectionType effectiveType = subLedger.getPaymentSelection() != null
                    ? subLedger.getPaymentSelection()
                    : payment.getPaymentMode();

            if (effectiveType == null) continue;
            if (effectiveType == PaymentSelectionType.CASH) continue;

            String refSite = subLedger.getReferenceSite() != null ? subLedger.getReferenceSite() : "";
            String slid    = subLedger.getId()            != null ? subLedger.getId()            : "";
            clickPaymentButton(subLedger.getSubLedger(), refSite, slid)
                    .fillAndSubmit(payment);

            JavascriptExecutorUtils.waitForExtJS();
        }
    }
}
