package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.reports.SubLedgerRowStepRegistry;
import com.aventstack.extentreports.ExtentTest;
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

import com.gsl.pages.Finance.Operations.Common.ArPaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import static com.gsl.utils.Selenium.ExtJsUtils.setExtJsComboByName;

public class ARVoucherSubLedgerInformation extends VoucherSubLedgerInformationBase {

    public ARVoucherSubLedgerInformation() {
        super(VoucherDomain.AR);
    }

    @Override
    protected PaymentInformationPopup createPaymentPopup() {
        return new ArPaymentInformationPopup();
    }
    private static final By SUBLEDGERNAMEBLANK=By.xpath("(//div[contains(@class,'x-window')]//table[contains(@class,'x-grid-item')])[last()]//tr[last()]//td[3]");
    private static final String SUBLEDGERSELECTION_XPATH="//span[text()='%s']";
    private static final By SUBLEDGERAMOUNTCR = By.xpath("//tr[.//td/div[normalize-space()='ABUL KASHEM MOLLHA'] and .//td/div[normalize-space()='Hena Embroidery']]//td[contains(@data-columnid,'numbercolumn')]//div");
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
     *                     false → Pass 1 only (used for DOC_NO_ADJUSTMENT scenarios)
     */
    public ARVoucherSubLedgerInformation fillSubLedgerRows(
            MergedFinanceArVoucherData data, boolean includeRetry) {

        final List<SubLedgerInformation> sls = data.getSubLedgerInformationList();

        // Start each test's row grouping clean (ThreadLocal — safe under parallel="methods").
        SubLedgerRowStepRegistry.clear();

        // ── Pass 1: set sub-ledger name and amount ────────────────────────────
        for (int i = 0; i < sls.size(); i++) {
            final int idx = i;
            final SubLedgerInformation sl = sls.get(i);
            String rawSlName = sl.getSubLedger();
            if (rawSlName == null || rawSlName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "SubLedger name is null/empty at row index " + i
                    + " (TestCaseID='" + sl.getTestCaseID() + "', LineNo='" + sl.getLineNo() + "'). "
                    + "Check Excel 'SubLedgerInformation' sheet — column header must be exactly 'Sub Ledger'."
                );
            }
            final String slName  = rawSlName.trim();
            final String slId    = sl.getId()            != null ? sl.getId().trim()            : "";
            final String refSite = sl.getReferenceSite() != null ? sl.getReferenceSite().trim() : "";

            // Create the per-row group node and register it so the later passes
            // (reference site, adjustment, payment) report under this same row.
            final String rowKey = SubLedgerRowStepRegistry.key(sl.getLineNo(), idx);
            ExtentTest rowNode = StepExecutor.group(
                    "SL Row " + (idx + 1) + " — " + slName
                    + " | Cr: " + sl.getAmountCr()
                    + " | GL: " + sl.getGeneralLedger());
            SubLedgerRowStepRegistry.put(rowKey, rowNode);

            StepExecutor.stepUnder(rowNode, "Fill — name / amount", () -> {
                if (idx > 0) addSubLedgerRow();
                try {
                    setSubLedgerName(slName, slId, refSite, idx);
                    setSubLedgerAmount(sl.getAmountCr().toPlainString(), idx);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted setting sub-ledger amount on row " + idx, e);
                }
                Log.debug("Row {} | SL={} | Amt={}", idx, sl.getSubLedger(), sl.getAmountCr());
            });
        }

        if (includeRetry) {
            // ── Pass 2: change reference site only when explicitly requested ──
            for (int i = 0; i < sls.size(); i++) {
                final int idx = i;
                final SubLedgerInformation sl = sls.get(i);
                final String refSite = sl.getReferenceSite() != null ? sl.getReferenceSite() : "";
                ExtentTest rowNode = SubLedgerRowStepRegistry.get(
                        SubLedgerRowStepRegistry.key(sl.getLineNo(), idx));
                if (sl.isChangeReferenceSite()) {
                    StepExecutor.stepUnder(rowNode,
                            "Change Reference Site — " + (refSite.isEmpty() ? "(none)" : refSite),
                            () -> setSubLedgerReferenceSite(refSite, idx));
                } else {
                    ExtentLogger.info("[AR SL RefSite] Row " + idx
                            + ": skipping Pass-2 set (changeReferenceSite=false) — current value kept");
                }
            }
            // Row-0 retry — only if that row opted in
            {
                final SubLedgerInformation sl0 = sls.get(0);
                final String refSite = sl0.getReferenceSite() != null ? sl0.getReferenceSite() : "";
                ExtentTest rowNode = SubLedgerRowStepRegistry.get(
                        SubLedgerRowStepRegistry.key(sl0.getLineNo(), 0));
                if (sl0.isChangeReferenceSite()) {
                    StepExecutor.stepUnder(rowNode,
                            "Change Reference Site (row-0 retry) — " + (refSite.isEmpty() ? "(none)" : refSite),
                            () -> setSubLedgerReferenceSite(refSite, 0));
                }
            }
            // Read back actual values regardless — captures what the app populated
            for (int i = 0; i < sls.size(); i++) {
                String actual = getSubLedgerReferenceSite(i);
                if (actual != null && !actual.isEmpty()) sls.get(i).setReferenceSite(actual);
            }
        }

        // ── Adjustments (Adjust button → adjustment entry → details → back to grid) ──
        List<SubLedgerInformation> adjustRows = sls.stream()
                .filter(r -> r.getAdjustmentType() != null)
                .collect(Collectors.toList());

        for (SubLedgerInformation row : adjustRows) {
            final int idx = sls.indexOf(row);
            final String refSite = row.getReferenceSite() != null ? row.getReferenceSite() : "";
            final String slId    = row.getId()            != null ? row.getId()            : "";
            final AdjustmentType adjType = row.getAdjustmentType();
            ExtentTest rowNode = SubLedgerRowStepRegistry.get(
                    SubLedgerRowStepRegistry.key(row.getLineNo(), idx));

            StepExecutor.stepUnder(rowNode, "Adjust — Type: " + adjType, () -> {
                AdjustmentEntry adjustmentEntry = openAdjustmentEntry(row.getSubLedger(), refSite, slId);
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
            });
        }

        // ── Payment (Cheque/RTGS) ───────────────────────────────────────────────
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
        if (referenceSite == null || referenceSite.trim().isEmpty()) return this;
        String inputId = JavascriptExecutorUtils.openReferenceSiteCellEditor(
                DriverManager.getDriver(), rowIndex, "arvch_grid");
        if (inputId == null || inputId.isEmpty()) {
            ExtentLogger.warning("[AR SL RefSite] Row " + rowIndex
                    + ": native editor did not open — using JS injection fallback for '" + referenceSite + "'");
            Log.warn("[SL_REFSITE_NATIVE] AR row {} — falling back to JS injection for '{}'", rowIndex, referenceSite);
            JavascriptExecutorUtils.setSubLedgerReferenceSite(DriverManager.getDriver(), rowIndex, referenceSite);
            return this;
        }
        ExtentLogger.info("[AR SL RefSite] Row " + rowIndex + ": native path — typing '" + referenceSite + "'");
        By comboInput = By.id(inputId);
        SeleniumUtils.clear(comboInput, "SL_REFSITE");
        SeleniumUtils.sendKeys(comboInput, WaitType.VISIBLE, referenceSite, "SL_REFSITE");
        JavascriptExecutorUtils.waitForExtJS();
        By option = By.xpath("//li[@role='option'][normalize-space(.)='" + referenceSite + "']");
        SeleniumUtils.click(option, WaitType.CLICKABLE, "SL_REFSITE_OPTION_" + referenceSite);
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        ExtentLogger.info("[AR SL RefSite] Row " + rowIndex + ": selected '" + referenceSite + "' via native");
        return this;
    }

    private String getSubLedgerReferenceSite(int rowIndex) {
        return JavascriptExecutorUtils.getSubLedgerReferenceSiteName(DriverManager.getDriver(), rowIndex);
    }

    private ARVoucherSubLedgerInformation setSubLedgerAmount(String subLedgerAmount, int rowIndex) throws InterruptedException {
        String inputId = JavascriptExecutorUtils.openAmountCellEditor(
                DriverManager.getDriver(), rowIndex, "arvch_grid");
        if (inputId == null || inputId.isEmpty()) {
            throw new IllegalStateException(
                    "[SL_AMOUNT_NATIVE] Could not open Amount editor on row " + rowIndex
                    + " for AR grid [arvch_grid]");
        }
        By amountInput = By.id(inputId);
        SeleniumUtils.clear(amountInput, "SL_AMOUNT");
        SeleniumUtils.sendKeys(amountInput, WaitType.VISIBLE, subLedgerAmount, "SL_AMOUNT");
        // Tab commits the value to the ExtJS store record — without it the typed amount is never
        // written to the underlying record, causing "Amount is required" on Save.
        SeleniumUtils.pressTab(amountInput, "SL_AMOUNT");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    private By getSubLedgerNameSelection(String subLedgerName) {
        String xpath = String.format(SUBLEDGERSELECTION_XPATH, subLedgerName);
        System.out.println("Generated XPath: " + xpath);
        return By.xpath(xpath);
    }

    private void handlePaymentInformation(
            List<SubLedgerInformation> subLedgerList,
            List<PaymentInformation> paymentList) {

        for (SubLedgerInformation subLedger : subLedgerList) {
            final String lineNo = subLedger.getLineNo();
            if (lineNo == null || lineNo.isBlank()) continue;

            final PaymentInformation payment = paymentList.stream()
                    .filter(p -> p.getLineNo() != null
                            && p.getLineNo().equalsIgnoreCase(lineNo)
                            && (subLedger.getTestCaseID() == null || p.getTestCaseID() == null
                                || p.getTestCaseID().equalsIgnoreCase(subLedger.getTestCaseID())))
                    .findFirst()
                    .orElse(null);

            if (payment == null) continue;

            final PaymentSelectionType effectiveType = subLedger.getPaymentSelection() != null
                    ? subLedger.getPaymentSelection()
                    : payment.getPaymentMode();

            if (effectiveType == null) continue;
            if (effectiveType == PaymentSelectionType.CASH) continue;

            final String refSite = subLedger.getReferenceSite() != null ? subLedger.getReferenceSite() : "";
            final String slid    = subLedger.getId()            != null ? subLedger.getId()            : "";
            ExtentTest rowNode = SubLedgerRowStepRegistry.get(lineNo);
            StepExecutor.stepUnder(rowNode, "Payment — " + effectiveType.label(), () -> {
                clickPaymentButton(subLedger.getSubLedger(), refSite, slid)
                        .fillAndSubmit(payment);
                JavascriptExecutorUtils.waitForExtJS();
            });
        }
    }
}
