package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.gsl.driver.DriverManager;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.WaitType;
import com.gsl.pages.Finance.Operations.Common.AdjustmentAmountSnapshot;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.Finance.Operations.Common.ReferenceSiteWiseFIFOPopup;
import com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.reports.SubLedgerRowStepRegistry;
import com.aventstack.extentreports.ExtentTest;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.ReferenceSitewiseFIFOsetoff;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
import com.gsl.utils.BigDecimalUtils;
import com.gsl.utils.Log;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.gsl.pages.Finance.Operations.Common.ArPaymentInformationPopup;
import com.gsl.pages.Finance.Operations.Common.PaymentInformationPopup;
import com.gsl.config.ConfigFactory;

public class ARVoucherSubLedgerInformation extends VoucherSubLedgerInformationBase {

    public ARVoucherSubLedgerInformation() {
        super(VoucherDomain.AR);
    }

    @Override
    protected PaymentInformationPopup createPaymentPopup() {
        return new ArPaymentInformationPopup();
    }

    @Override
    protected String gridItemId() {
        return "arvch_grid";
    }

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

            final String rowKey = SubLedgerRowStepRegistry.key(sl.getLineNo(), idx);
            final String rowLabel = "SL Row " + (idx + 1) + " — " + slName
                    + " | Cr: " + sl.getAmountCr()
                    + " | GL: " + sl.getGeneralLedger();
            ExtentTest rowNode = StepExecutor.group(rowLabel);
            SubLedgerRowStepRegistry.put(rowKey, rowNode, rowLabel);

            SubLedgerRowStepRegistry.stepUnderRow(rowKey, "Fill — name / amount", () -> {
                if (idx > 0) addSubLedgerRow();
                try {
                    setSubLedgerName(slName, slId, refSite, idx);
                    ReportLogger.trace("[SL Row " + (idx + 1) + "] slName=" + slName
                            + " slId=" + (slId.isEmpty() ? "—" : slId)
                            + " refSite=" + (refSite.isEmpty() ? "—" : refSite));
                    setSubLedgerAmount(sl.getAmountCr().toPlainString(), idx);
                    ReportLogger.trace("[SL Row " + (idx + 1) + "] amountCr → typed=" + sl.getAmountCr());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted setting sub-ledger amount on row " + idx, e);
                }
                Log.debug("Row {} | SL={} | Amt={}", idx, sl.getSubLedger(), sl.getAmountCr());
            });
            notifyFill(l -> l.onRowFilled(idx));
        }

        if (includeRetry) {
            applyReferenceSitePass(sls);
        }

        // ── Adjustments ───────────────────────────────────────────────────────
        List<SubLedgerInformation> adjustRows = sls.stream()
                .filter(r -> r.getAdjustmentType() != null)
                .collect(Collectors.toList());

        for (SubLedgerInformation row : adjustRows) {
            final int idx = sls.indexOf(row);
            final String refSite = row.getReferenceSite() != null ? row.getReferenceSite() : "";
            final String slId    = row.getId()            != null ? row.getId()            : "";
            final AdjustmentType adjType = row.getAdjustmentType();
            final String rowKey = SubLedgerRowStepRegistry.key(row.getLineNo(), idx);

            SubLedgerRowStepRegistry.stepUnderRow(rowKey, "Adjust — Type: " + adjType, () -> {
                AdjustmentEntry adjustmentEntry = openAdjustmentEntry(row.getSubLedger(), refSite, slId);
                notifyFill(l -> l.onAdjustmentOpened(idx, adjType));
                boolean needsConfirmation = data.getGridRelation() == SelectDocumentVariant.GridRelation.GREATER
                        || data.getGridRelation() == SelectDocumentVariant.GridRelation.LESS;
                Log.debug("AdjType: {} | GridRelation: {} | needsConfirmation: {}", adjType, data.getGridRelation(), needsConfirmation);

                if (adjType == AdjustmentType.SELECT_DOCUMENT) {
                    SelectDocumentPopup popup = adjustmentEntry.openAdjustedDocumentMenu().selectDocument();
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
                    notifyFill(l -> l.onAdjustmentApplied(idx, adjType));
                    if (needsConfirmation) {
                        adjustmentEntry.clickOKExpectingConfirmation().clickProceedAnyway();
                    } else {
                        adjustmentEntry.clickOK();
                    }

                } else if (adjType == AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF) {
                    String lineNo = row.getLineNo();
                    List<ReferenceSitewiseFIFOsetoff> fifoRows =
                            data.getReferenceSitewiseFIFOList() != null
                                    ? data.getReferenceSitewiseFIFOList() : List.of();
                    List<ReferenceSitewiseFIFOsetoff> refSites = fifoRows.stream()
                            .filter(r -> lineNo != null && lineNo.equalsIgnoreCase(r.getLineNo()))
                            .collect(Collectors.toList());

                    ReferenceSiteWiseFIFOPopup fifoPopup =
                            adjustmentEntry.openAdjustedDocumentMenu().openReferenceSiteWiseFIFOPopup();
                    for (int i = 0; i < refSites.size(); i++) {
                        ReferenceSitewiseFIFOsetoff rs = refSites.get(i);
                        ExtentLogger.info("[FIFO] Reference Site: " + rs.getReferenceSiteName()
                                + " | Site: " + rs.getSiteName());
                        if (i > 0) fifoPopup.addRow();
                        fifoPopup.selectReferenceSiteInCombo(rs.getReferenceSiteName());
                    }
                    fifoPopup.clickAutoAdjust();

                    AdjustmentAmountSnapshot snapshot = adjustmentEntry.captureAmounts();
                    Log.info("RefSiteFIFO amounts — txn={} adj={} pending={}",
                             snapshot.getTransactionAmount(),
                             snapshot.getAdjustedAmount(),
                             snapshot.getPendingToAdjust());
                    ExtentLogger.info("[FIFO] Amounts — Txn: " + snapshot.getTransactionAmount()
                            + " | Adjusted: " + snapshot.getAdjustedAmount()
                            + " | Pending: " + snapshot.getPendingToAdjust());

                    notifyFill(l -> l.onAdjustmentApplied(idx, adjType));
                    if (needsConfirmation) {
                        adjustmentEntry.clickOKExpectingConfirmation().clickProceedAnyway();
                    } else {
                        adjustmentEntry.clickOK();
                    }

                } else if (adjType == AdjustmentType.FIFO_SETOFF) {
                    adjustmentEntry.openAdjustedDocumentMenu().fifoSetOff();
                    notifyFill(l -> l.onAdjustmentApplied(idx, adjType));
                    if (needsConfirmation) {
                        adjustmentEntry.clickOKExpectingConfirmation().clickProceedAnyway();
                    } else {
                        adjustmentEntry.clickOK();
                    }
                }
            });
        }

        // ── Payment (Cheque/RTGS) ─────────────────────────────────────────────
        if (data.isPaymentProvided()) {
            handlePaymentInformation(sls, data.getPaymentInformationList());
        }

        return this;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AdjustmentEntry openAdjustmentEntry(String subLedgerName,
                                                 String referenceSite, String slid) {
        clickAdjust(subLedgerName, referenceSite, slid);
        // The Adjusted Document "•••" menu is opened right before its option is used, not here: the popup's
        // before-fill UI checks run in between and the floating menu closes meanwhile (run 20261009_164937,
        // FIN-AR-ADD-DOC-03: "Select Document" not interactable 15 s after the menu opened).
        return new AdjustmentEntry(this);
    }

    /** AR's Sub-Ledger picker has an extra Ledger column (confirmed live, user screenshot 2026-10-09). */
    @Override
    protected java.util.List<String> subLedgerPickerColumns() {
        return java.util.List.of("Name", "Id", "Ledger", "Class Name");
    }

    private void setSubLedgerName(String subledgerName, String id, String referenceSite, int rowIndex) {
        setSubLedgerNameNative(subledgerName, id, referenceSite, rowIndex);
    }
}
