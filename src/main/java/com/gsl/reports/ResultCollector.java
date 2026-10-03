package com.gsl.reports;

import com.gsl.annotations.TestCaseId;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import org.testng.ITestResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe, in-memory log of every GV/AR/AP voucher test invocation of the current run, fed
 * from {@code TestListener.onTestSuccess/onTestFailure/onTestSkipped} and read once at suite
 * finish by {@link CoverageWorkbookBuilder}. Exists because {@code live/run_delta.ndjson} only
 * carries test name/status/duration — not the variant id, scenario text or skip reason the
 * coverage workbook needs.
 *
 * <p>Failure-safe by contract: {@link #record} never throws (a collector problem must not affect
 * a test's own status), and tests that are not GV/AR/AP voucher tests are ignored.
 *
 * <p><b>Variant id per test</b> — General Voucher rows carry it as
 * {@link MergedFinanceGeneralVoucherData#getScenarioTypeLabel()} (the data sources set it to
 * {@code GeneralVoucherVariant#getVariantId()}; {@code MasterTestData.testcaseid} is NOT usable
 * for GV because {@code MergedFinGeneralVoucherDataSupplier} overwrites it with the positional
 * {@code FIN-GV-...-NN} id). AR/AP rows carry it as the placeholder's {@code MasterTestData}
 * test-case token; AP Edit detail rows carry it as their edit-scenario constant name.
 */
public final class ResultCollector {

    private static final List<CoverageResult> RESULTS = new CopyOnWriteArrayList<>();

    private ResultCollector() {}

    /**
     * Records one finished invocation. Never throws.
     *
     * @param result TestNG's result for the invocation
     * @param status {@code PASS}, {@code FAIL} or {@code SKIP}
     */
    public static void record(ITestResult result, String status) {
        try {
            // A retried attempt is superseded by the next attempt's own result — not a real outcome.
            if ("SKIP".equals(status) && result.wasRetried()) {
                return;
            }
            TestCaseId tc = result.getMethod().getConstructorOrMethod().getMethod()
                    .getAnnotation(TestCaseId.class);
            if (tc == null) {
                return;
            }
            String module = moduleOf(tc.value());
            if (module == null) {
                return;
            }
            Object[] params = result.getParameters() == null ? new Object[0] : result.getParameters();
            InvocationData inv = null;
            Object data = null;
            for (Object p : params) {
                if (inv == null && p instanceof InvocationData) inv = (InvocationData) p;
                if (data == null && (p instanceof MergedFinanceGeneralVoucherData
                        || p instanceof MergedFinanceArVoucherData
                        || p instanceof MergedFinanceApVoucherData)) data = p;
            }
            String testCaseId = inv != null
                    ? String.format("%s-%02d", tc.value(), inv.getInvocationIndex())
                    : tc.value();
            String description = result.getMethod().getDescription();
            MasterTestData master = masterOf(data);
            if (master != null && master.getTestcasename() != null && !master.getTestcasename().isBlank()) {
                description = master.getTestcasename();
            }
            RESULTS.add(CoverageResult.builder()
                    .module(module)
                    .flow(flowOf(tc.value()))
                    .testCaseId(testCaseId)
                    .variantId(variantIdOf(data))
                    .scenarioType(scenarioTypeOf(data))
                    .description(description == null ? "" : description)
                    .methodName(result.getMethod().getMethodName())
                    .status(status)
                    .durationMs(Math.max(0, result.getEndMillis() - result.getStartMillis()))
                    .reason(firstLine(result.getThrowable()))
                    .build());
        } catch (Exception e) {
            com.gsl.utils.Log.warn("[ResultCollector] Could not record result: {}", e.getMessage());
        }
    }

    /**
     * Deduplicated view: when the same (testCaseId, variantId) was recorded more than once (a
     * retry), only the last attempt is kept. Insertion order preserved.
     */
    public static List<CoverageResult> snapshot() {
        Map<String, CoverageResult> latest = new LinkedHashMap<>();
        for (CoverageResult r : RESULTS) {
            latest.put(r.getModule() + "|" + r.getFlow() + "|" + r.getTestCaseId() + "|" + r.getVariantId(), r);
        }
        return new ArrayList<>(latest.values());
    }

    /** Test hook: forget everything recorded so far. */
    public static void clear() {
        RESULTS.clear();
    }

    /** {@code GV}/{@code AR}/{@code AP} from a {@code FIN-<module>-...} test-case id, else {@code null}. */
    static String moduleOf(String baseTestCaseId) {
        if (baseTestCaseId == null) return null;
        String[] parts = baseTestCaseId.split("-");
        if (parts.length < 3 || !"FIN".equals(parts[0])) return null;
        return switch (parts[1]) {
            case "GV", "AR", "AP" -> parts[1];
            default -> null;
        };
    }

    /** Flow label from a {@code FIN-<module>-<FLOW>-...} test-case id. */
    static String flowOf(String baseTestCaseId) {
        String[] parts = baseTestCaseId.split("-");
        return switch (parts[2]) {
            case "ADD" -> "Create";
            case "EDIT" -> "Edit";
            case "REVERT" -> "Revert";
            case "DELETE" -> "Delete";
            case "UI" -> "UI Validation";
            default -> parts[2];
        };
    }

    private static MasterTestData masterOf(Object data) {
        if (data instanceof MergedFinanceGeneralVoucherData) return ((MergedFinanceGeneralVoucherData) data).getMasterTestData();
        if (data instanceof MergedFinanceArVoucherData) return ((MergedFinanceArVoucherData) data).getMasterTestData();
        if (data instanceof MergedFinanceApVoucherData) return ((MergedFinanceApVoucherData) data).getMasterTestData();
        return null;
    }

    static String variantIdOf(Object data) {
        if (data instanceof MergedFinanceGeneralVoucherData) {
            return nz(((MergedFinanceGeneralVoucherData) data).getScenarioTypeLabel());
        }
        MasterTestData master = masterOf(data);
        if (master != null && master.getTestcaseid() != null && !master.getTestcaseid().isBlank()) {
            return master.getTestcaseid();
        }
        if (data instanceof MergedFinanceApVoucherData
                && ((MergedFinanceApVoucherData) data).getEditScenarioType() != null) {
            return ((MergedFinanceApVoucherData) data).getEditScenarioType().name();
        }
        return "";
    }

    private static String scenarioTypeOf(Object data) {
        if (data instanceof MergedFinanceGeneralVoucherData) return nz(((MergedFinanceGeneralVoucherData) data).getScenarioTypeLabel());
        if (data instanceof MergedFinanceArVoucherData) return nz(((MergedFinanceArVoucherData) data).getScenarioTypeLabel());
        if (data instanceof MergedFinanceApVoucherData) return nz(((MergedFinanceApVoucherData) data).getScenarioTypeLabel());
        return "";
    }

    private static String firstLine(Throwable t) {
        if (t == null) return "";
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) return t.getClass().getSimpleName();
        String line = msg.strip().lines().findFirst().orElse("");
        return line.length() > 300 ? line.substring(0, 300) + "..." : line;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
