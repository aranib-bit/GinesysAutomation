package com.gsl.pages.pagecomponent.Finance.Operations.APVoucher;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.driver.DriverManager;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SelectDocument;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.gsl.pages.Finance.Operations.Common.ApPaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import static com.gsl.utils.Selenium.ExtJsUtils.setExtJsComboByName;

public class APVoucherSubLedgerInformation extends VoucherSubLedgerInformationBase {

    public APVoucherSubLedgerInformation() {
        super(VoucherDomain.AP);
    }

    @Override
    protected PaymentInformationPopup createPaymentPopup() {
        return new ApPaymentInformationPopup();
    }

    private static final By SUBLEDGERNAMEBLANK = By.xpath(
            "(//div[contains(@class,'x-window')]//table[contains(@class,'x-grid-item')])" +
            "[last()]//tr[last()]//td[3]");

    private static final String SUBLEDGERSELECTION_XPATH = "//span[text()='%s']";

    private static final String AP_GRID_ITEM_ID = "apvch_grid";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Advance payment (On Account = Yes).
     * Sub-ledger + amount + reference site only; no SelectDocument / FIFO adjustments.
     * GL fields (General Ledger, Ledger Group Name) are populated by the app's async
     * handler, triggered by combo.blur() inside the JS layer. The AP advance combo store
     * does not carry GL metadata; the framework waits up to 12 s (30 × 400 ms) for the
     * app's AJAX to land, gated on Ext.Ajax.isLoading() === false.
     */
    public APVoucherSubLedgerInformation fillSubLedgerRowsForAdvance(
            MergedFinanceApVoucherData data, boolean includeRetry) {

        List<SubLedgerInformation> sls = data.getSubLedgerInformationList();
        fillSubLedgerBase(sls, includeRetry);
        if (data.isPaymentProvided()) {
            handlePaymentInformation(sls, data.getPaymentInformationList());
        }
        return this;
    }

    /**
     * Document adjustment (On Account = No).
     * Sub-ledger + amount + reference site + SelectDocument / FIFO adjustments + payment.
     * The AP document combo store carries GL metadata (same as AR), so GL fields are
     * populated directly from the combo record (fast path — polls exit on first iteration).
     * combo.blur() is still called by the shared JS layer; it is harmless when GL is
     * already supplied by the combo.
     */
    public APVoucherSubLedgerInformation fillSubLedgerRowsForDocument(
            MergedFinanceApVoucherData data, boolean includeRetry) {

        List<SubLedgerInformation> sls = data.getSubLedgerInformationList();
        fillSubLedgerBase(sls, includeRetry);
        fillAdjustments(sls, data);
        if (data.isPaymentProvided()) {
            handlePaymentInformation(sls, data.getPaymentInformationList());
        }
        return this;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Pass 1 (sub-ledger name + amount) and Pass 2 (reference site re-apply). Common to both modes. */
    private void fillSubLedgerBase(List<SubLedgerInformation> sls, boolean includeRetry) {

        // ── Pass 1: set sub-ledger name and amount (Dr) ───────────────────────
        for (int i = 0; i < sls.size(); i++) {
            SubLedgerInformation sl = sls.get(i);
            if (i > 0) addSubLedgerRow();
            String rawSlName = sl.getSubLedger();
            if (rawSlName == null || rawSlName.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    "SubLedger name is null/empty at row index " + i
                    + " (TestCaseID='" + sl.getTestCaseID() + "', LineNo='" + sl.getLineNo() + "').");
            }
            String slName  = rawSlName.trim();
            String slId    = sl.getId()            != null ? sl.getId().trim()            : "";
            String refSite = sl.getReferenceSite() != null ? sl.getReferenceSite().trim() : "";
            try {
                setSubLedgerName(slName, slId, refSite, i);
                setSubLedgerAmount(sl.getAmountDr().toPlainString(), i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted setting sub-ledger amount on row " + i, e);
            }
            if (sl.getDeductTds() != null && !sl.getDeductTds().trim().isEmpty()) {
                JavascriptExecutorUtils.setDeductTdsForRow(
                        DriverManager.getDriver(), i, sl.getDeductTds().trim(), AP_GRID_ITEM_ID);
            }
            Log.debug("Row {} | SL={} | AmtDr={} | DeductTDS={}",
                    i, sl.getSubLedger(), sl.getAmountDr(), sl.getDeductTds());
        }

        if (includeRetry) {
            // ── Pass 2: re-apply reference sites ─────────────────────────────
            for (int i = 0; i < sls.size(); i++) {
                String refSite = sls.get(i).getReferenceSite() != null
                        ? sls.get(i).getReferenceSite() : "";
                setSubLedgerReferenceSite(refSite, i);
            }
            // Row-0 retry
            {
                String refSite = sls.get(0).getReferenceSite() != null
                        ? sls.get(0).getReferenceSite() : "";
                setSubLedgerReferenceSite(refSite, 0);
            }
            for (int i = 0; i < sls.size(); i++) {
                String actual = getSubLedgerReferenceSite(i);
                if (actual != null && !actual.isEmpty()) sls.get(i).setReferenceSite(actual);
            }
        }
    }

    /** SelectDocument / FIFO adjustments. Used only in document adjustment mode. */
    private void fillAdjustments(List<SubLedgerInformation> sls, MergedFinanceApVoucherData data) {

        List<SubLedgerInformation> adjustRows = sls.stream()
                .filter(r -> r.getAdjustmentType() != null)
                .collect(Collectors.toList());

        for (SubLedgerInformation row : adjustRows) {
            String refSite = row.getReferenceSite() != null ? row.getReferenceSite() : "";
            String slId    = row.getId()            != null ? row.getId()            : "";
            AdjustmentEntry adjustmentEntry = openAdjustmentEntry(row.getSubLedger(), refSite, slId);
            AdjustmentType adjType = row.getAdjustmentType();
            boolean needsConfirmation = false; // AP voucher has no GridRelation confirmation popup

            if (adjType == AdjustmentType.SELECT_DOCUMENT) {
                SelectDocumentPopup popup = adjustmentEntry.selectDocument();
                String lineNo = row.getLineNo();
                List<SelectDocument> docs = data.getSelectDocumentList().stream()
                        .filter(d -> lineNo != null && lineNo.equalsIgnoreCase(d.getLineNo()))
                        .collect(Collectors.toList());
                for (SelectDocument doc : docs) {
                    popup.selectDocument(doc.getDocumentNumber());
                }
                popup.clickSelect();
                for (SelectDocument doc : docs) {
                    BigDecimal excelAmount = doc.getAdjustmentAmount();
                    if (excelAmount == null || excelAmount.compareTo(BigDecimal.ZERO) == 0) continue;
                    if (excelAmount.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException(
                            "Adjustment amount cannot be negative for document ["
                            + doc.getDocumentNumber() + "]. Got: " + excelAmount.toPlainString());
                    }
                    String uiStr = adjustmentEntry.getAdjustedAmountForDocument(doc.getDocumentNumber());
                    BigDecimal uiAmount = BigDecimalUtils.safeParse(uiStr);
                    if (excelAmount.compareTo(uiAmount) != 0) {
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
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AdjustmentEntry openAdjustmentEntry(String subLedgerName,
                                                 String referenceSite, String slid) {
        clickAdjust(subLedgerName, referenceSite, slid);
        AdjustmentEntry entry = new AdjustmentEntry(this);
        entry.openAdjustedDocumentMenu();
        return entry;
    }

    private void setSubLedgerName(String subledgerName, String id, String referenceSite, int rowIndex) {
        if (rowIndex == 0) {
            JavascriptExecutorUtils.setSubLedger(DriverManager.getDriver(), subledgerName, id, referenceSite, AP_GRID_ITEM_ID);
        } else {
            JavascriptExecutorUtils.setSubLedgerForRow(DriverManager.getDriver(), rowIndex, subledgerName, id, referenceSite, AP_GRID_ITEM_ID);
        }
    }

    private void addSubLedgerRow() {
        JavascriptExecutorUtils.addSubLedgerRow(DriverManager.getDriver(), AP_GRID_ITEM_ID);
    }

    private void setSubLedgerReferenceSite(String referenceSite, int rowIndex) {
        JavascriptExecutorUtils.setSubLedgerReferenceSite(DriverManager.getDriver(), rowIndex, referenceSite, AP_GRID_ITEM_ID);
    }

    private String getSubLedgerReferenceSite(int rowIndex) {
        return JavascriptExecutorUtils.getSubLedgerReferenceSiteName(DriverManager.getDriver(), rowIndex, AP_GRID_ITEM_ID);
    }

    // AP Voucher uses Debit amount — grid field name is 'amount' same as AR, but grid itemId may differ.
    // Verify AP_GRID_ITEM_ID against the live form if amount setting fails.
    private void setSubLedgerAmount(String subLedgerAmount, int rowIndex) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        Thread.sleep(500);
        String result = (String) js.executeScript(
                "try {" +
                        "    var amount = parseFloat(arguments[0]);" +
                        "    var rowIndex = parseInt(arguments[1]);" +
                        "    var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + AP_GRID_ITEM_ID + "]')[0];" +
                        "    if (!childGrid) return 'ERROR: " + AP_GRID_ITEM_ID + " not found';" +
                        "    var store = childGrid.getStore();" +
                        "    if (!store || store.getCount() === 0) return 'ERROR: store empty';" +
                        "    var rec = store.getAt(rowIndex);" +
                        "    if (!rec) return 'ERROR: row ' + rowIndex + ' not found';" +
                        "    rec.set('amount', amount);" +
                        "    return 'SUCCESS: row=' + rowIndex + ' amount=' + rec.get('amount');" +
                        "} catch(e) {" +
                        "    return 'ERROR: ' + e.message;" +
                        "}",
                subLedgerAmount, rowIndex
        );
        System.out.println("AP setSubLedgerAmount[" + rowIndex + "] result: " + result);
        Thread.sleep(300);
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
            clickPaymentButton(subLedger.getSubLedger(), refSite, slid).fillAndSubmit(payment);
            JavascriptExecutorUtils.waitForExtJS();
        }
    }
}
