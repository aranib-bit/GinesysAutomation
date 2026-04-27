package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType.ItemSelectionType;
import com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType.SetSelectionType;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.Procurement.ReceiptHeader;
import com.gsl.testdata.dto.ItemBlock;
import com.gsl.testdata.dto.MergedReceiptData;
import com.gsl.testdata.dto.ScanItemRow;
import com.gsl.testdata.dto.SelectItemRow;
import com.gsl.testdata.dto.SelectSetRow;
import com.gsl.testdata.dto.ItemDetailsRow;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;
import one.util.streamex.IntStreamEx;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

public class MergedReceiptDataSupplier {

    /* ---------- Utility ---------- */

    private <E extends Enum<E>> boolean isInEnum(String value, Class<E> enumClass) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            Enum.valueOf(enumClass,
                    value.trim().toUpperCase().replace(" ", "_"));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /* ---------- DataSupplier ---------- */

    @DataSupplier(name = "getData")
    public StreamEx<MasterTestData> getData(Method method) {
        String methodName = method.getName();
        List<MasterTestData> allTestCases = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read()
                .toList();
        return StreamEx.of(allTestCases)
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()));
    }

    @DataSupplier(name = "receiptData", flatMap = true)
    public StreamEx<MergedReceiptData> dataSupplier(Method method) {

        String methodName = method.getName();
        System.out.println("Incoming Method Name: " + methodName);

        /* ---------- Read Excel ---------- */

        List<MasterTestData> allTestCases = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read()
                .toList();

        List<ItemDetailsRow> allItemDetailsRows = use(XlsxReader.class)
                .withTarget(ItemDetailsRow.class)
                .withSource(ConfigFactory.getExcelDataConfig().receiptsPath())
                .read()
                .toList();

        List<ScanItemRow> scanItemRows = use(XlsxReader.class)
                .withTarget(ScanItemRow.class)
                .withSource(ConfigFactory.getExcelDataConfig().receiptsPath())
                .read()
                .toList();

        List<SelectItemRow> selectItemRows = use(XlsxReader.class)
                .withTarget(SelectItemRow.class)
                .withSource(ConfigFactory.getExcelDataConfig().receiptsPath())
                .read()
                .toList();

        List<SelectSetRow> selectSetRows = use(XlsxReader.class)
                .withTarget(SelectSetRow.class)
                .withSource(ConfigFactory.getExcelDataConfig().receiptsPath())
                .read()
                .toList();

        List<ReceiptHeader> headers = use(XlsxReader.class)
                .withTarget(ReceiptHeader.class)
                .withSource(ConfigFactory.getExcelDataConfig().receiptsPath())
                .read()
                .toList();

        /* ---------- Filter test cases by method ---------- */

        List<MasterTestData> filteredCases = allTestCases.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .collect(Collectors.toList());

        /* ---------- Expand data per invocation ---------- */

        return StreamEx.of(filteredCases)
                .flatMap(testCase -> {

                    String testCaseId = testCase.getTestcaseid();

                    ReceiptHeader caseHeader = headers.stream()
                            .filter(h -> testCaseId.equalsIgnoreCase(h.getTestCaseId()))
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "No ReceiptHeader found for TestCaseId: " + testCaseId));

                    List<ItemBlock> itemBlocks = allItemDetailsRows.stream()
                            .filter(r -> testCaseId.equalsIgnoreCase(r.getTestCaseId()))
                            .map(main -> {

                                String majorMethod = main.getItemSelectionMajorMethod();
                                String selectionWay = main.getItemSetSelectionWay();

                                List<ScanItemRow> scans = List.of();
                                List<SelectItemRow> selects = List.of();
                                List<SelectSetRow> sets = List.of();

                                if ("Item".equalsIgnoreCase(majorMethod)
                                        && isInEnum(selectionWay, ItemSelectionType.class)) {

                                    if ("Scan Item".equalsIgnoreCase(selectionWay)) {
                                        scans = scanItemRows.stream()
                                                .filter(s -> testCaseId.equalsIgnoreCase(s.getTestCaseId()))
                                                .collect(Collectors.toList());
                                    } else if ("Select Item".equalsIgnoreCase(selectionWay)) {
                                        selects = selectItemRows.stream()
                                                .filter(s -> testCaseId.equalsIgnoreCase(s.getTestCaseId()))
                                                .collect(Collectors.toList());
                                    }

                                } else if ("Set".equalsIgnoreCase(majorMethod)
                                        && isInEnum(selectionWay, SetSelectionType.class)) {

                                    if ("Select Set".equalsIgnoreCase(selectionWay)) {
                                        sets = selectSetRows.stream()
                                                .filter(s -> testCaseId.equalsIgnoreCase(s.getTestCaseId()))
                                                .collect(Collectors.toList());
                                    }
                                }

                                return ItemBlock.builder()
                                        .itemDetails(main)
                                        .scanItems(scans)
                                        .selectItems(selects)
                                        .selectSets(sets)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    int totalInvocations = Optional
                            .ofNullable(testCase.getInvocationcount())
                            .orElse(1);

                    return IntStreamEx.range(1, totalInvocations + 1)
                            .mapToObj(invocationIndex ->
                                    MergedReceiptData.builder()
                                            .testCaseId(testCaseId)
                                            .masterTestData(testCase)
                                            .receiptHeader(caseHeader)
                                            .itemBlocks(itemBlocks)
                                            .invocationIndex(invocationIndex)
                                            .totalInvocations(totalInvocations)
                                            .build()
                            );
                });
    }
}
