package com.tests.Finance.Operations.Vouchers;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.enums.AdjustmentType;
import com.gsl.enums.TestAuthor;
import com.gsl.model.finance.FinPost;
import com.gsl.pages.Finance.Operations.ArVoucherListPage;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Finance.MainMenuType;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.Finance.Operations.ArVoucherAdd;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.pages.Finance.Operations.Common.AdjustmentEntry;
import com.gsl.pages.Finance.Operations.Common.SelectDocumentPopup;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.pages.Finance.Operations.Common.DocumentPopup;
import com.gsl.reports.ArVoucherExtentDescriptionLogger;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedFinArVoucherDataSupplier;
import com.gsl.utils.dataprovider.source.finance.DbArVoucherDataSource;
import com.gsl.validation.db.finance.ARVoucherDbValidator;
import com.gsl.validation.db.finance.StateCapture;
import com.gsl.validation.db.finance.VoucherDbContext;
import com.tests.BaseTest;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class CreateArVoucher extends BaseTest {
    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To create new AR Voucher of Finance from UI",
            dataProvider = "dataSupplier", dataProviderClass = MergedFinArVoucherDataSupplier.class)

    public void createArVoucher(MergedFinanceArVoucherData data) throws InterruptedException {
        System.out.println("Create AR Voucher of Finance from UI");
        System.out.println("Running test for: " + data.getMasterTestData().getTestcaseid());

        // ── Point 1: Lazy data generation ────────────────────────────────────────
        // In DB mode the DataSupplier emits lightweight placeholders (subLedgerInformationList
        // is empty). Fetch fresh DB data NOW — at test-execution time — so each scenario
        // sees the current state of the database, not a snapshot taken before all tests ran.
        if (data.getScenarioType() != null
                && (data.getSubLedgerInformationList() == null
                    || data.getSubLedgerInformationList().isEmpty())) {
            System.out.println("[DB mode] Placeholder detected for scenario="
                    + data.getScenarioType() + ". Fetching fresh data from DB…");
            data = DbArVoucherDataSource.loadFreshForScenario(
                    data, "createArVoucher");
        }

        if (data.getScenarioType() != null) {
            System.out.println("[DB mode] Scenario type: " + data.getScenarioType()
                    + " | testCaseId: " + data.getMasterTestData().getTestcaseid());
        }

        // ── Log rich test data description to Extent Report ──────────────────
        // Full data is available here (lazy loading complete). Reference, Remarks,
        // Sub-Ledgers, Select Docs, and Payment Info are all live runtime values.
        ArVoucherExtentDescriptionLogger.log(data);

        // 1. Login to the application
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(data.getMasterTestData().getUsername(), data.getMasterTestData().getPassword());

        // 2. Use the navigation utility to get to the correct screen
        ArVoucherListPage arvoucherListPage = NavigationUtils.navigateToFinanceSideMenu(homePage,
                MainMenuType.OPERATIONS,
                OperationsMenuType.ARVOUCHER, ArVoucherListPage.class);

        List<SubLedgerInformation> subLedgers = data.getSubLedgerInformationList();
        LocalDate docdate=data.getDocumentInformation().getDocumentdate();
        String formattedDocDate = docdate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        System.out.println("Document Date" + formattedDocDate);
        ArVoucherAdd addPage = arvoucherListPage.clickAdd()
                .setDocScheme(data.getDocumentInformation().getVoucherno())
                .setDocumentDate(formattedDocDate)
                .setReference(data.getDocumentInformation().getReference())
                .clickCashBankSearchIcon()
                .selectCashBank(data.getDocumentInformation().getCashbank())
                .setRemarks(data.getDocumentInformation().getRemarks());

        // Pass 1: Add each subledger row, set SL name, reference site, and amount.
        // addSubLedgerRow() is called only for i > 0 because the grid always starts
        // with one empty row (index 0) already present; calling it for i = 0 would
        // create a duplicate blank row before the first subledger.
        // Reference site is set here per row as part of SL selection. However, the
        // application's built-in slCode combo 'select' event handler (fired during
        // each SL selection) can silently overwrite row 0's referenceSiteCode field
        // when rows 1, 2, 3 … are processed. Pass 2 re-applies every reference site
        // as the authoritative final write, guaranteeing correctness for all rows.
        for (int i = 0; i < subLedgers.size(); i++) {
            SubLedgerInformation row = subLedgers.get(i);
            if (i > 0) {
                addPage.addSubLedgerRow();   // adds a new empty grid row for rows 1, 2, 3 …
            }
            String slId    = row.getId()            != null ? row.getId()            : "";
            String refSite = row.getReferenceSite() != null ? row.getReferenceSite() : "";
            addPage.setsubledgername(row.getSubLedger(), slId, refSite, i)
                   .setSubLedgerAmount(row.getAmountCr().toPlainString(), i);
        }

        // Pass 2: Re-apply reference sites for every row as the authoritative final write.
        // Rules:
        //   • If Excel provides a Reference Site name  → match and set that specific site.
        //   • If Excel leaves Reference Site blank      → set the 1st available site from
        //     the store (JS falls back to store.getAt(0) when target is empty).
        // We therefore call setSubLedgerReferenceSite for EVERY row, not just non-empty ones.
        // Pass 2 — normal order.
        for (int i = 0; i < subLedgers.size(); i++) {
            String refSite = subLedgers.get(i).getReferenceSite() != null
                    ? subLedgers.get(i).getReferenceSite() : "";
            // Always call — empty refSite triggers the "first available" fallback in JS.
            addPage.setSubLedgerReferenceSite(refSite, i);
        }
        // Retry row 0: the first setSubLedgerReferenceSite call initialises the Reference
        // Site combo's store proxy; the initial load may return 0 records (proxy URL just
        // configured).  After rows 1+ are processed the proxy is warm; re-applying row 0
        // guarantees it gets the correct value.
        {
            String refSite = subLedgers.get(0).getReferenceSite() != null
                    ? subLedgers.get(0).getReferenceSite() : "";
            addPage.setSubLedgerReferenceSite(refSite, 0);
        }

        // Read back actual Reference Site values set by the UI.
        // If a row's site was blank (DB mode or blank Excel), the JS auto-picked the first
        // available site. Capture the actual value so clickAdjust uses the correct XPath discriminator.
        for (int i = 0; i < subLedgers.size(); i++) {
            String actual = addPage.getSubLedgerReferenceSite(i);
            if (actual != null && !actual.isEmpty()) {
                subLedgers.get(i).setReferenceSite(actual);
            }
        }

        // Adjustment: process ALL rows that have AdjustmentType set, not just the first one.
        // For each such row, click its row-level Adjust button (using subLedger + referenceSite
        // as XPath discriminators), open the Adjusted Document menu, and perform the appropriate
        // adjustment action. This is safe in edit scenarios where UI row order may differ from
        // Excel row order.
        List<SubLedgerInformation> adjustRows = subLedgers.stream()
                .filter(r -> r.getAdjustmentType() != null)
                .collect(Collectors.toList());

        for (SubLedgerInformation row : adjustRows) {
            String refSite = row.getReferenceSite() != null ? row.getReferenceSite() : "";
            AdjustmentEntry adjustmentEntry = addPage.clickAdjust(row.getSubLedger(), refSite);
            AdjustmentType adjType = row.getAdjustmentType();
            System.out.println("AdjType is" + adjType);
            if (adjType == AdjustmentType.SELECT_DOCUMENT) {
                System.out.println("Inside if Select Document");
                SelectDocumentPopup popup = adjustmentEntry.selectDocument();
                String subLedgerLineNo = row.getLineNo();
                List<SelectDocument> docs = data.getSelectDocumentList().stream()
                        .filter(d -> subLedgerLineNo != null && subLedgerLineNo.equalsIgnoreCase(d.getLineNo()))
                        .collect(Collectors.toList());
                System.out.println("Filtered docs size for LineNo " + subLedgerLineNo + ": " + docs.size());
                for (SelectDocument doc : docs) {
                    System.out.println("Select Document " + doc.getDocumentNumber());
                    popup.selectDocument(doc.getDocumentNumber());
                }
                popup.clickSelect();

                // Per-document check: after returning to AdjustmentEntry, the grid shows
                // one row per selected document. For each document, compare the UI Adjusted
                // Amount with the Excel value and correct it if they differ.
                for (SelectDocument doc : docs) {
                    BigDecimal excelAmount = doc.getAdjustmentAmount();
                    if (excelAmount == null || excelAmount.compareTo(BigDecimal.ZERO) == 0) continue;

                    String uiStr = adjustmentEntry.getAdjustedAmountForDocument(doc.getDocumentNumber());
                    BigDecimal uiAmount = (uiStr != null && !uiStr.trim().isEmpty())
                            ? new BigDecimal(uiStr.replace(",", "").trim())
                            : BigDecimal.ZERO;

                    System.out.println("Doc " + doc.getDocumentNumber() + " — UI: " + uiAmount + " | Excel: " + excelAmount);
                    if (excelAmount.compareTo(uiAmount) != 0) {
                        System.out.println("Mismatch. Updating Adjusted Amount to: " + excelAmount.toPlainString());
                        adjustmentEntry.setAdjustedAmountForDocument(doc.getDocumentNumber(), excelAmount.toPlainString());
                    }
                }

                adjustmentEntry.clickOK();
            } else if (adjType == AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF) {
                adjustmentEntry.referenceSiteWiseFIFO().showReferenceSiteSummary().clickOK();
            } else if (adjType == AdjustmentType.FIFO_SETOFF) {
                adjustmentEntry.fifoSetOff().clickOK();
            }
        }

        // Payment Information: fill Cheque/RTGS popup for rows that have Payment Selection set
        if (data.isPaymentProvided()) {
            addPage.handlePaymentInformation(
                    data.getSubLedgerInformationList(),
                    data.getPaymentInformationList());
        }

        // Capture invoice finpost state BEFORE voucher creation (for adjamt cascade validation)
        List<String> invoiceNos = data.getSelectDocumentList() != null
                ? data.getSelectDocumentList().stream()
                        .map(SelectDocument::getDocumentNumber)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        StateCapture stateCapture = new StateCapture();
        Map<String, FinPost> preState = stateCapture.captureInvoiceState(invoiceNos);

        DocumentPopup savePopup = addPage.clickSave();
        com.gsl.pages.Common.DocumentMessageBox messageBox = data.isNeedsIgnoreProceed()
                ? savePopup.verifyWarningMessage().clickIgnoreProceed()
                : savePopup.waitForMessageBox();
        String docNo = messageBox.extractDocumentNo();

        System.out.println("Extracted Document No: " + docNo);

        // Capture invoice finpost state AFTER voucher creation
        Map<String, FinPost> postState = stateCapture.captureInvoiceState(invoiceNos);

        // DB Validation — Levels 1 → 4b
        new ARVoucherDbValidator().validate(
                VoucherDbContext.builder()
                        .voucherNo(docNo)
                        .testData(data)
                        .preState(preState)
                        .postState(postState)
                        .build());

        // Dismiss success popup → close AR Voucher form → confirm discard → logout
        messageBox.clickOk();
        addPage.close()
               .clickYes();

        HomePage homePage1 = new HomePage();
        homePage1.logoutFromApplication();
    }

}
