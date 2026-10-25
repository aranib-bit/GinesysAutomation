package com.gsl.reports;

import com.gsl.annotations.TestCaseId;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.validation.ui.UiFailureCollector;
import com.gsl.validation.ui.UiValidationFailure;
import org.testng.ITestResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe, in-memory log of every GV/AR/AP voucher test invocation of the current run, fed
 * from {@code TestListener.onTestSuccess/onTestFailure/onTestSkipped} and read once at suite
 * finish by {@link CoverageWorkbookBuilder}. Exists because {@code live/run_delta.ndjson} only
 * carries test name/status/duration - not the variant id, scenario text, header fields, failure
 * detail or retry history the coverage workbook needs.
 *
 * <p>Failure-safe by contract: {@link #record} never throws (a collector problem must not affect
 * a test's own status), and tests that are not GV/AR/AP voucher tests are ignored. Every optional
 * detail (title, header rows, failure detail, screenshot, voucher no.) is captured in its own
 * try/catch so one missing piece only blanks that piece.
 *
 * <p><b>Variant id per test</b> - General Voucher rows carry it as
 * {@link MergedFinanceGeneralVoucherData#getScenarioTypeLabel()} (the data sources set it to
 * {@code GeneralVoucherVariant#getVariantId()}; {@code MasterTestData.testcaseid} is NOT usable
 * for GV because {@code MergedFinGeneralVoucherDataSupplier} overwrites it with the positional
 * {@code FIN-GV-...-NN} id). AR/AP rows carry it as the placeholder's {@code MasterTestData}
 * test-case token; AP Edit detail rows carry it as their edit-scenario constant name.
 *
 * <p><b>Retries</b> - a retried attempt arrives as a SKIP with {@code wasRetried()}; it is not an
 * outcome, but each one is counted per (module, flow, test case, variant) so the final result can
 * report {@code retries} and whether it passed only on a retry.
 */
public final class ResultCollector {

    private static final List<CoverageResult> RESULTS = new CopyOnWriteArrayList<>();
    private static final Map<String, Integer> RETRIES = new ConcurrentHashMap<>();

    private ResultCollector() {}

    /**
     * Records one finished invocation. Never throws.
     *
     * @param result TestNG's result for the invocation
     * @param status {@code PASS}, {@code FAIL} or {@code SKIP}
     */
    public static void record(ITestResult result, String status) {
        try {
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
            String flow = flowOf(tc.value());
            String variantId = variantIdOf(data);
            String key = module + "|" + flow + "|" + testCaseId + "|" + variantId;

            // A retried attempt is superseded by the next attempt's own result - not a real outcome,
            // but it is the only trace of how many tries the final result took.
            if ("SKIP".equals(status) && result.wasRetried()) {
                RETRIES.merge(key, 1, Integer::sum);
                return;
            }

            String description = result.getMethod().getDescription();
            MasterTestData master = masterOf(data);
            if (master != null && master.getTestcasename() != null && !master.getTestcasename().isBlank()) {
                description = master.getTestcasename();
            }
            Throwable t = result.getThrowable();

            // Title exactly as the report shows it (the node was named in TestListener.onTestStart).
            TestTitle title = TestTitle.parse(extentTitle());
            String sentence = title.description();
            if (sentence.isBlank()) sentence = nz(buildSentence(data));
            if (sentence.isBlank()) sentence = nz(description);
            int runIndex = title.runIndex();
            int runTotal = title.runTotal();
            if (runIndex == 0 && inv != null) {
                runIndex = inv.getInvocationIndex();
                runTotal = inv.getTotalInvocations();
            }

            List<UiValidationFailure> uiFails = uiFailures();
            FailureClassifier.Outcome outcome = FailureClassifier.classify(t, status, uiFails);

            List<String[]> header = List.of();
            List<String[]> expected = List.of();
            try {
                header = VoucherReportRows.withoutHeaderRow(headerRowsOf(data));
            } catch (NullPointerException e) {
                // UI-only tests carry no voucher header data - nothing to render, not a problem.
                com.gsl.utils.Log.debug("[ResultCollector] no header data for this test: {}", e.toString());
            } catch (Throwable e) {
                com.gsl.utils.Log.warn("[ResultCollector] header rows unavailable: {}", e.toString());
            }
            try {
                expected = VoucherReportRows.withoutHeaderRow(expectedRowsOf(data));
            } catch (Throwable e) {
                com.gsl.utils.Log.warn("[ResultCollector] expected rows unavailable: {}", e.toString());
            }

            int retries = RETRIES.getOrDefault(key, 0);
            String scenarioSummary = nz(buildRichName(data));
            if (scenarioSummary.isBlank()) scenarioSummary = nz(scenarioTypeOf(data));

            RESULTS.add(CoverageResult.builder()
                    .module(module)
                    .flow(flow)
                    .testCaseId(testCaseId)
                    .variantId(variantId)
                    .scenarioType(scenarioTypeOf(data))
                    .description(description == null ? "" : description)
                    .methodName(result.getMethod().getMethodName())
                    .status(status)
                    .durationMs(Math.max(0, result.getEndMillis() - result.getStartMillis()))
                    .reason(outcome.summary())
                    .titleDescription(sentence)
                    .runIndex(runIndex)
                    .runTotal(runTotal)
                    .scenarioSummary(scenarioSummary)
                    .startMs(result.getStartMillis())
                    .endMs(result.getEndMillis())
                    .thread(Thread.currentThread().getName())
                    .voucherNo(voucherNo(data))
                    .user(currentUser())
                    .author(authorOf(result))
                    .retries(retries)
                    .passedOnRetry("PASS".equals(status) && retries > 0)
                    .failureCategory(outcome.category())
                    .failureMessage(outcome.fullMessage())
                    .failingStep(failingStep())
                    .screenshotFile(nz(screenshot()))
                    .failures(outcome.details())
                    .headerFields(header)
                    .expectedRows(expected)
                    .expectedSummary(VoucherReportRows.summariseExpected(expected))
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
        RETRIES.clear();
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

    // -- capture helpers (each failure-safe on its own) -------------------------------------------

    private static String extentTitle() {
        try {
            if (ExtentManager.hasTest()) {
                return ExtentManager.getExtentTest().getModel().getName();
            }
        } catch (Throwable ignored) {
            // fall back to a rebuilt title
        }
        return null;
    }

    private static String buildSentence(Object data) {
        try {
            return data == null ? null : new FinanceDomainReporter().buildSentence(data);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String buildRichName(Object data) {
        try {
            return data == null ? null : new FinanceDomainReporter().buildRichName(data);
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<String[]> headerRowsOf(Object data) {
        if (data instanceof MergedFinanceGeneralVoucherData d) return GeneralVoucherExtentDescriptionLogger.headerRowsOf(d);
        if (data instanceof MergedFinanceArVoucherData d) return ArVoucherExtentDescriptionLogger.headerRowsOf(d);
        if (data instanceof MergedFinanceApVoucherData d) return ApVoucherExtentDescriptionLogger.headerRowsOf(d);
        return List.of();
    }

    private static List<String[]> expectedRowsOf(Object data) {
        if (data instanceof MergedFinanceGeneralVoucherData d) return GeneralVoucherExtentDescriptionLogger.expectedRowsOf(d);
        if (data instanceof MergedFinanceArVoucherData d) return ArVoucherExtentDescriptionLogger.expectedRowsOf(d);
        if (data instanceof MergedFinanceApVoucherData d) return ApVoucherExtentDescriptionLogger.expectedRowsOf(d);
        return List.of();
    }

    /** UI-contract FAILs: the holder's full result list (filtered to FAIL) else the deferred collector. */
    private static List<UiValidationFailure> uiFailures() {
        try {
            List<UiValidationFailure> all = ActualResultsHolder.get().getUiValidationResults();
            List<UiValidationFailure> out = new ArrayList<>();
            if (all != null) {
                for (UiValidationFailure f : all) if (f.isFail()) out.add(f);
            }
            if (out.isEmpty()) out.addAll(UiFailureCollector.pending());
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static String voucherNo(Object data) {
        try {
            String v = ActualResultsHolder.get().getVoucherNo();
            if ((v == null || v.isBlank()) && data instanceof MergedFinanceGeneralVoucherData gv) {
                v = gv.getExistingVoucherNo();
            }
            return nz(v);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String failingStep() {
        try {
            for (StepDurationBuffer.StepRecord s : StepDurationBuffer.getAll()) {
                if (!s.passed()) return s.stepName();
            }
        } catch (Throwable ignored) {
            // blank
        }
        return "";
    }

    private static String screenshot() {
        try {
            return ScreenshotPolicy.lastPendingPath();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String currentUser() {
        try {
            var u = com.gsl.driver.DriverManager.getCurrentUser();
            return u == null ? "" : nz(u.username());
        } catch (Throwable t) {
            return "";
        }
    }

    private static String authorOf(ITestResult result) {
        try {
            var fa = result.getMethod().getConstructorOrMethod().getMethod()
                    .getAnnotation(com.gsl.annotations.FrameworkAnnotation.class);
            return fa == null ? "" : String.valueOf(fa.value());
        } catch (Throwable t) {
            return "";
        }
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
