package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.query.AdmClsQuery;
import com.gsl.enums.AdjustmentType;
import com.gsl.model.finance.FinGl;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.*;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.IntStreamEx;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

/**
 * Loads AR Voucher test data from Excel (existing behaviour — unchanged).
 * This is the exact body of the original MergedFinArVoucherDataSupplier.dataSupplier()
 * extracted verbatim.
 */
public class ExcelArVoucherDataSource implements TestDataSource<MergedFinanceArVoucherData> {

    @Override
    public List<MergedFinanceArVoucherData> load(String methodName) {

        List<MasterTestData> masterData = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        List<DocumentInformation> docList = use(XlsxReader.class)
                .withTarget(DocumentInformation.class)
                .withSource(ConfigFactory.getExcelDataConfig().arVoucherPath())
                .read().toList();

        List<SubLedgerInformation> subLedgerList = use(XlsxReader.class)
                .withTarget(SubLedgerInformation.class)
                .withSource(ConfigFactory.getExcelDataConfig().arVoucherPath())
                .read().toList();

        // Diagnostic: log every row read from SubLedgerInformation sheet
        Log.debug("[ExcelArVoucherDataSource] SubLedgerInformation rows read: {}", subLedgerList.size());
        subLedgerList.forEach(sl -> Log.debug(
                "  [SubLedgerInformation] TestCaseID='{}' | LineNo='{}' | SubLedger='{}' | Id='{}'",
                sl.getTestCaseID(), sl.getLineNo(), sl.getSubLedger(), sl.getId()));

        Jdbi jdbi = DatabaseManager.getJdbi();
        subLedgerList.forEach(sl -> sl.setSlType(AdmClsQuery.findClsnameBySlId(jdbi, sl.getId())));

        List<SelectDocument> selectDocList = use(XlsxReader.class)
                .withTarget(SelectDocument.class)
                .withSource(ConfigFactory.getExcelDataConfig().arVoucherPath())
                .read().toList();

        List<ReferenceSitewiseFIFOsetoff> refFifoList = use(XlsxReader.class)
                .withTarget(ReferenceSitewiseFIFOsetoff.class)
                .withSource(ConfigFactory.getExcelDataConfig().arVoucherPath())
                .read().toList();

        List<PaymentInformation> paymentList = use(XlsxReader.class)
                .withTarget(PaymentInformation.class)
                .withSource(ConfigFactory.getExcelDataConfig().arVoucherPath())
                .read().toList();

        // ── Filter 1: match test method name ────────────────────────────────────
        List<MasterTestData> nameMatched = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        Log.info("[ExcelArVoucherDataSource] MasterTestData total rows read : {}", masterData.size());
        Log.info("[ExcelArVoucherDataSource] Rows matching method '{}' : {}", methodName, nameMatched.size());
        masterData.stream()
                .filter(tc -> !methodName.equalsIgnoreCase(tc.getTestcasename()))
                .forEach(tc -> Log.warn(
                        "  [SKIPPED by filter-1] testcasename='{}' does not match method='{}'",
                        tc.getTestcasename(), methodName));

        // ── Filter 2: TestCaseID must exist in DocumentInformation ───────────
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

        Log.info("[ExcelArVoucherDataSource] Test cases after both filters : {}", filteredCases.size());

        // Compute grand total up front so every emitted object gets a globally unique
        // invocationIndex. This ensures TestListener builds distinct IDs:
        // FIN-AR-ADD-01 for row 1, FIN-AR-ADD-02 for row 2, etc.
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

                    // Fail-fast: catch null/empty subLedger early with a clear diagnostic message
                    subLedgers.forEach(sl -> {
                        if (sl.getSubLedger() == null || sl.getSubLedger().trim().isEmpty()) {
                            throw new IllegalStateException(
                                "[ExcelArVoucherDataSource] SubLedger is null/empty for TestCaseID='" + tcId
                                + "' LineNo='" + sl.getLineNo() + "'. "
                                + "Verify that the Excel column header in the 'SubLedgerInformation' sheet "
                                + "is exactly 'Sub Ledger' (case-sensitive, with one space). "
                                + "ZeroCell maps @Column(name=\"Sub Ledger\") and silently returns null on mismatch."
                            );
                        }
                    });

                    boolean hasSelectDocument = subLedgers.stream()
                            .anyMatch(s -> s.getAdjustmentType() == AdjustmentType.SELECT_DOCUMENT);

                    boolean hasFifo = subLedgers.stream()
                            .anyMatch(s -> s.getAdjustmentType() == AdjustmentType.FIFO_SETOFF
                                    || s.getAdjustmentType() == AdjustmentType.REFERENCE_SITEWISE_FIFO_SETOFF);

                    List<SelectDocument> selectedDocs = hasSelectDocument
                            ? selectDocList.stream()
                                    .filter(s -> s.getTestCaseID().equalsIgnoreCase(tcId))
                                    .collect(Collectors.toList())
                            : List.of();

                    List<ReferenceSitewiseFIFOsetoff> fifoSet = hasFifo
                            ? refFifoList.stream()
                                    .filter(r -> r.getLineNo().equalsIgnoreCase(tcId))
                                    .collect(Collectors.toList())
                            : List.of();

                    List<PaymentInformation> payments = paymentList.stream()
                            .filter(p -> p.getTestCaseID().equalsIgnoreCase(tcId))
                            .collect(Collectors.toList());

                    int totalInvocations = tc.getInvocationcount() != null
                            ? tc.getInvocationcount() : 1;

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

                    boolean needsIgnoreProceed = isBank && (!allLinesHavePayment || anyInstrumentNoMissing);

                    return IntStreamEx.range(1, totalInvocations + 1)
                            .mapToObj(invIdx ->
                                    MergedFinanceArVoucherData.builder()
                                            .masterTestData(tc)
                                            .documentInformation(doc)
                                            .subLedgerInformationList(subLedgers)
                                            .selectDocumentList(selectedDocs)
                                            .referenceSitewiseFIFOList(fifoSet)
                                            .paymentInformationList(payments)
                                            .paymentProvided(paymentProvided)
                                            .needsIgnoreProceed(needsIgnoreProceed)
                                            .invocationIndex(globalCounter.incrementAndGet())
                                            .totalInvocations(grandTotal)
                                            // scenarioType intentionally null — Excel mode
                                            .build()
                            );
                })
                .collect(Collectors.toList());
    }
}
