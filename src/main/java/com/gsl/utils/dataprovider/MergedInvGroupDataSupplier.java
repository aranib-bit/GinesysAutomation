package com.gsl.utils.dataprovider;

import com.gsl.config.ConfigFactory;
import com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Group.Group;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedInventoryGroupData;
import io.github.sskorol.core.DataSupplier;
import io.github.sskorol.data.XlsxReader;
import one.util.streamex.StreamEx;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.sskorol.data.TestDataReader.use;

public class MergedInvGroupDataSupplier {

    private <E extends Enum<E>> boolean isInEnum(String value, Class<E> enumClass) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            Enum.valueOf(enumClass, value.trim().toUpperCase().replace(" ", "_"));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @DataSupplier(name = "dataSupplier", flatMap = true)
    public StreamEx<MergedInventoryGroupData> dataSupplier(Method method) {
        String methodName = method.getName();
        System.out.println("📌 Incoming Method Name: " + methodName);

        // Load MasterTestData
        List<MasterTestData> allTestCases = use(XlsxReader.class)
                .withTarget(MasterTestData.class)
                .withSource(ConfigFactory.getExcelDataConfig().MasterTestData())
                .read().toList();

        // Load ItemGroup (CreateGroup sheet)
        List<Group> allGroups = use(XlsxReader.class)
                .withTarget(Group.class)
                .withSource(ConfigFactory.getExcelDataConfig().inventorygroupPath())
                .read().toList();

        // ✅ Filter cases where (1) methodName matches testcasename AND (2) TestCaseID exists in both excels
        List<MasterTestData> filteredCases = allTestCases.stream()
                .filter(tc -> methodName.equalsIgnoreCase(tc.getTestcasename()))
                .filter(tc -> allGroups.stream()
                        .anyMatch(g -> g.getTestCaseId().equalsIgnoreCase(tc.getTestcaseid())))
                .collect(Collectors.toList());

        System.out.println("Matched testcases count: " + filteredCases.size());
        filteredCases.forEach(tc -> System.out.println("→ " + tc.getTestcaseid() + " | " + tc.getTestcasename()));


        // ✅ Merge matched records
        return StreamEx.of(filteredCases).map(testCase -> {
            String testCaseId = testCase.getTestcaseid();
            System.out.println("✅ Matched TestCaseID: " + testCaseId);

            Group matchedGroup = allGroups.stream()
                    .filter(g -> g.getTestCaseId().equalsIgnoreCase(testCaseId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "❌ No matching Group found in ItemGroup.xlsx for TestCaseID: " + testCaseId
                    ));

            System.out.println("Part 2");
            System.out.println("Matched testcases count: " + filteredCases.size());
            filteredCases.forEach(tc -> System.out.println("→ " + tc.getTestcaseid() + " | " + tc.getTestcasename()));

            return MergedInventoryGroupData.builder()
                    .testCaseId(testCaseId)
                    .masterTestData(testCase)
                    .group(matchedGroup)
                    .build();
        });
    }
}
