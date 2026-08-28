package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.query.AdmClsQuery;
import com.gsl.enums.AdjustmentType;
import com.gsl.model.finance.FinGl;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.*;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.io.OptionalColumnMerger;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.IntStreamEx;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

public class ExcelApVoucherDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    @Override
    public List<MergedFinanceApVoucherData> load(String methodName) {

        List<MasterTestData> masterData = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        List<DocumentInformation> docList = use(XlsxReader.class)
                .withTarget(DocumentInformation.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        docList.forEach(doc -> {
            if (doc.getDocumentdate() == null) {
                throw new IllegalStateException(
                    "[ExcelApVoucherDataSource] documentdate is null for TestCaseID='" + doc.getTestCaseId() + "'. " +
                    "Ensure the 'Date' column in the 'DocumentInformation' sheet is non-empty and uses a supported " +
                    "format: dd/MM/yyyy, dd-MM-yyyy, or yyyy-MM-dd.");
            }
        });

        List<SubLedgerInformation> subLedgerList = use(XlsxReader.class)
                .withTarget(SubLedgerInformation.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        Log.debug("[ExcelApVoucherDataSource] SubLedgerInformation rows read: {}", subLedgerList.size());
        subLedgerList.forEach(sl -> Log.debug(
                "  [SubLedgerInformation] TestCaseID='{}' | LineNo='{}' | SubLedger='{}' | Id='{}'",
                sl.getTestCaseID(), sl.getLineNo(), sl.getSubLedger(), sl.getId()));

        // ── Optional: "On Account Confirm" column (Apache POI direct read) ─────────
        // XlsxReader throws for any @Column field whose header is absent in the sheet.
        // onAccountConfirm is NOT annotated with @Column for this reason.
        readOptionalOnAccountConfirm(
                ConfigFactory.getExcelDataConfig().apVoucherPath(), docList);
        // ── End optional ─────────────────────────────────────────────────────────

        // ── Optional: "Deduct TDS Confirm" column (Apache POI direct read) ────────
        // XlsxReader throws for any @Column field whose header is absent in the sheet.
        // To avoid that hard failure for a new optional column, we bypass XlsxReader
        // entirely and use Apache POI to probe the sheet ourselves.
        readOptionalDeductTdsConfirm(
                ConfigFactory.getExcelDataConfig().apVoucherPath(), subLedgerList);
        // ── End optional ─────────────────────────────────────────────────────────

        Jdbi jdbi = DatabaseManager.getJdbi();
        subLedgerList.forEach(sl -> sl.setSlType(AdmClsQuery.findClsnameBySlId(jdbi, sl.getId())));

        List<SelectDocument> selectDocList = use(XlsxReader.class)
                .withTarget(SelectDocument.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        List<ReferenceSitewiseFIFOsetoff> refFifoList = use(XlsxReader.class)
                .withTarget(ReferenceSitewiseFIFOsetoff.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        List<PaymentInformation> paymentList = use(XlsxReader.class)
                .withTarget(PaymentInformation.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        List<TdsAndNetPayable> tdsNetPayableList = use(XlsxReader.class)
                .withTarget(TdsAndNetPayable.class)
                .withSource(ConfigFactory.getExcelDataConfig().apVoucherPath())
                .read().toList();

        Log.debug("[ExcelApVoucherDataSource] TDS&NetPayable rows read: {}", tdsNetPayableList.size());
        tdsNetPayableList.forEach(t -> Log.debug(
                "  [TDS&NetPayable] TestCaseID='{}' | LineNo='{}' | TDSName='{}'",
                t.getTestCaseID(), t.getLineNo(), t.getTdsName()));

        // ── Filter 1: match test method name ────────────────────────────────────
        List<MasterTestData> nameMatched = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        Log.info("[ExcelApVoucherDataSource] MasterTestData total rows read : {}", masterData.size());
        Log.info("[ExcelApVoucherDataSource] Rows matching method '{}' : {}", methodName, nameMatched.size());
        masterData.stream()
                .filter(tc -> !methodName.equalsIgnoreCase(tc.getTestcasename()))
                .forEach(tc -> Log.warn(
                        "  [SKIPPED by filter-1] testcasename='{}' does not match method='{}'",
                        tc.getTestcasename(), methodName));

        // ── Filter 2: TestCaseID must exist in DocumentInformation ───────────────
        List<MasterTestData> filteredCases = nameMatched.stream()
                .filter(tc -> {
                    boolean found = docList.stream()
                            .anyMatch(d -> d.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid()));
                    if (!found) {
                        Log.warn(
                                "  [SKIPPED by filter-2] TestCaseID='{}' has no row in DocumentInformation sheet",
                                tc.getTestcaseid());
                    }
                    return found;
                })
                .collect(Collectors.toList());

        Log.info("[ExcelApVoucherDataSource] Test cases after both filters : {}", filteredCases.size());

        int grandTotal = filteredCases.stream()
                .mapToInt(tc -> tc.getInvocationcount() != null ? tc.getInvocationcount() : 1)
                .sum();
        AtomicInteger globalCounter = new AtomicInteger(0);

        return filteredCases.stream()
                .flatMap(tc -> {
                    String tcId = tc.getTestcaseid();

                    DocumentInformation doc = docList.stream()
                            .filter(d -> d.getTestCaseId().equalsIgnoreCase(tcId))
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalStateException("Missing DocumentInformation for: " + tcId));

                    List<SubLedgerInformation> subLedgers = subLedgerList.stream()
                            .filter(s -> s.getTestCaseID().equalsIgnoreCase(tcId))
                            .collect(Collectors.toList());

                    subLedgers.forEach(sl -> {
                        if (sl.getSubLedger() == null || sl.getSubLedger().trim().isEmpty()) {
                            throw new IllegalStateException(
                                "[ExcelApVoucherDataSource] SubLedger is null/empty for TestCaseID='" + tcId
                                + "' LineNo='" + sl.getLineNo() + "'. "
                                + "Verify that the Excel column header in the 'SubLedgerInformation' sheet "
                                + "is exactly 'Sub Ledger' (case-sensitive, with one space). "
                                + "ZeroCell maps @Column(name=\"Sub Ledger\") and silently returns null on mismatch."
                            );
                        }
                    });

                    boolean onAccount = doc.isOnAccount();

                    List<SelectDocument>              selectedDocs;
                    List<ReferenceSitewiseFIFOsetoff> fifoSet;
                    List<TdsAndNetPayable>             tdsRows;

                    if (onAccount) {
                        // Advance payment — SelectDocument and FIFO are both skipped
                        selectedDocs = List.of();
                        fifoSet      = List.of();
                        // TDS: one row per sub-ledger line (TDS.lineNo = SubLedger.lineNo)
                        tdsRows = tdsNetPayableList.stream()
                                .filter(t -> t.getTestCaseID().equalsIgnoreCase(tcId))
                                .collect(Collectors.toList());
                    } else {
                        // Document payment — SelectDocument is mandatory in AP
                        selectedDocs = selectDocList.stream()
                                .filter(s -> s.getTestCaseID().equalsIgnoreCase(tcId))
                                .collect(Collectors.toList());

                        boolean hasFifo = subLedgers.stream()
                                .anyMatch(s -> s.getAdjustmentType() == AdjustmentType.FIFO_SETOFF
                                        || s.getAdjustmentType() == AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF);
                        fifoSet = hasFifo
                                ? refFifoList.stream()
                                        .filter(r -> r.getTestCaseID() != null
                                                  && r.getTestCaseID().equalsIgnoreCase(tcId))
                                        .collect(Collectors.toList())
                                : List.of();

                        tdsRows = List.of();
                    }

                    boolean tdsProvided = !tdsRows.isEmpty();

                    List<PaymentInformation> payments = paymentList.stream()
                            .filter(p -> p.getTestCaseID().equalsIgnoreCase(tcId))
                            .collect(Collectors.toList());

                    boolean paymentProvided = !payments.isEmpty();

                    Set<String> subLedgerLineNos = subLedgers.stream()
                            .map(SubLedgerInformation::getLineNo)
                            .filter(ln -> ln != null && !ln.isBlank())
                            .collect(Collectors.toSet());

                    Set<String> paymentLineNos = payments.stream()
                            .map(PaymentInformation::getLineNo)
                            .filter(ln -> ln != null && !ln.isBlank())
                            .collect(Collectors.toSet());

                    boolean allLinesHavePayment = !subLedgerLineNos.isEmpty()
                            && paymentLineNos.containsAll(subLedgerLineNos);

                    boolean anyInstrumentNoMissing = payments.stream()
                            .anyMatch(p -> p.getInstrumentNo() == null || p.getInstrumentNo().isBlank());

                    FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
                    FinGl cashBankGl = (doc.getCashbank() != null && !doc.getCashbank().isBlank())
                            ? glDao.findByGlName(doc.getCashbank()) : null;
                    boolean isBank = cashBankGl != null && "B".equalsIgnoreCase(cashBankGl.getSrctype());

                    boolean needsPaymentInfoWarning = isBank && (!allLinesHavePayment || anyInstrumentNoMissing);

                    int totalInvocations = tc.getInvocationcount() != null
                            ? tc.getInvocationcount() : 1;

                    return IntStreamEx.range(1, totalInvocations + 1)
                            .mapToObj(invIdx ->
                                    MergedFinanceApVoucherData.builder()
                                            .masterTestData(tc)
                                            .documentInformation(doc)
                                            .subLedgerInformationList(subLedgers)
                                            .selectDocumentList(selectedDocs)
                                            .referenceSitewiseFIFOList(fifoSet)
                                            .paymentInformationList(payments)
                                            .tdsAndNetPayableList(tdsRows)
                                            .onAccount(onAccount)
                                            .tdsProvided(tdsProvided)
                                            .paymentProvided(paymentProvided)
                                            .needsPaymentInfoWarning(needsPaymentInfoWarning)
                                            .invocationIndex(globalCounter.incrementAndGet())
                                            .totalInvocations(grandTotal)
                                            .build()
                            );
                })
                .collect(Collectors.toList());
    }

    /**
     * Reads the optional "On Account Confirm" column from the DocumentInformation sheet —
     * bypassing XlsxReader so that an absent column header never causes a hard failure — and
     * merges values into {@code docList} via {@link DocumentInformation#setOnAccountConfirm(String)},
     * keyed on TestCaseID (case-insensitive). Mechanics live in {@link OptionalColumnMerger}.
     */
    private static void readOptionalOnAccountConfirm(
            String excelPath, List<DocumentInformation> docList) {
        OptionalColumnMerger.mergeByOneKey(
                excelPath, "DocumentInformation", "TestCaseID", "On Account Confirm",
                docList, DocumentInformation::getTestCaseId, DocumentInformation::setOnAccountConfirm,
                "[ExcelApVoucherDataSource]");
    }

    /**
     * Reads the optional "Deduct TDS Confirm" column from the SubLedgerInformation sheet —
     * bypassing XlsxReader so that an absent column header never causes a hard failure — and
     * merges values into {@code subLedgerList} via {@link SubLedgerInformation#setDeductTdsConfirm(String)},
     * keyed on TestCaseID (case-insensitive) + LineNo. Mechanics live in {@link OptionalColumnMerger}.
     */
    private static void readOptionalDeductTdsConfirm(
            String excelPath, List<SubLedgerInformation> subLedgerList) {
        OptionalColumnMerger.mergeByTwoKeys(
                excelPath, "SubLedgerInformation", "TestCaseID", "LineNo", "Deduct TDS Confirm",
                subLedgerList, SubLedgerInformation::getTestCaseID, SubLedgerInformation::getLineNo,
                SubLedgerInformation::setDeductTdsConfirm, "[ExcelApVoucherDataSource]");
    }
}
