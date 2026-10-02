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

/**
 * DataSupplier for Inventory Group creation tests (e.g. {@code CreateGroup#CreateDivision}) —
 * joins the shared {@code MasterTestData} sheet against the group-hierarchy Excel sheet
 * ({@code ConfigFactory.getExcelDataConfig().inventorygroupPath()}, "CreateGroup" sheet) on
 * {@code TestCaseID}, keeping only rows whose {@code testcasename} matches the invoking test
 * method's own name AND whose {@code TestCaseID} appears in both sheets.
 *
 * <p>{@code flatMap=true} — each matched {@link com.gsl.testdata.Inventory.Product_Definition.Item_Hierarchy.Group.Group}
 * row becomes its own {@link MergedInventoryGroupData} invocation (one test invocation per row,
 * unlike the Account Group suppliers' single-invocation model).
 *
 * <p>{@code TODO-confirm:} the {@code System.out.println}/emoji debug lines below look like
 * leftover diagnostic scaffolding rather than intentional reporting (unlike this framework's
 * {@code Log}/{@code ReportLogger} calls elsewhere) — left as-is since removing them is a
 * behavior change outside this Javadoc-only pass.
 */
public class MergedInvGroupDataSupplier {

    /**
     * {@code true} iff {@code value} (case/space-insensitive, spaces mapped to underscores) names
     * a constant of {@code enumClass}. {@code TODO-confirm:} not currently called anywhere in this
     * class's own {@link #dataSupplier} — appears to be leftover scaffolding (the sibling {@link
     * MergedReceiptDataSupplier} has an identical helper that IS used for its item/set-selection
     * filtering); left in place since removing it is a behavior change outside this Javadoc-only pass.
     */
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

    /**
     * Joins {@code MasterTestData} against the group-hierarchy sheet on {@code TestCaseID} for
     * every row matching {@code method}'s name — see the class Javadoc's join/filter rule.
     *
     * @param method the TestNG-injected test method; only its name is used, to match {@code
     *               testcasename} in the {@code MasterTestData} sheet
     * @throws IllegalStateException if a matched {@code TestCaseID} has no corresponding row in
     *                               the group-hierarchy sheet (should be unreachable given the
     *                               join already filters on that same {@code TestCaseID})
     */
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
