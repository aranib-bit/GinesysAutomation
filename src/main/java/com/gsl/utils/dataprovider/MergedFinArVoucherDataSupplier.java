package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.enums.AdjustmentType;
import com.gsl.model.finance.FinGl;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.*;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

public class MergedFinArVoucherDataSupplier {

    @DataSupplier(name = "FinanceArVoucherData", flatMap = true)
    public StreamEx<MergedFinanceArVoucherData> dataSupplier(Method method) {

        String methodName = method.getName();
        System.out.println("📌 Incoming Method Name: " + methodName);

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

        List<MasterTestData> filteredCases = masterData.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .filter(tc -> docList.stream()
                        .anyMatch(d -> d.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid())))
                .collect(Collectors.toList());

        return StreamEx.of(filteredCases)
                .flatMap(tc -> {

                    String tcId = tc.getTestcaseid();

                    DocumentInformation doc = docList.stream()
                            .filter(d -> d.getTestCaseId().equalsIgnoreCase(tcId))
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalStateException("❌ Missing DocumentInformation for: " + tcId));

                    List<SubLedgerInformation> subLedgers = subLedgerList.stream()
                            .filter(s -> s.getTestCaseID().equalsIgnoreCase(tcId))
                            .collect(Collectors.toList());

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
                            ? tc.getInvocationcount()
                            : 1;

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

                    FinGlDao glDao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
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
                                            .invocationIndex(invIdx)
                                            .totalInvocations(totalInvocations)
                                            .build()
                            );
                });
    }
}