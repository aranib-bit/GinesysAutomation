package com.gsl.listeners;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.FrameworkValidation;
import com.gsl.annotations.TestCaseId;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ExtentReport;
import com.gsl.reports.PreSuiteHealthCheck;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.Arrays;

public class TestListener implements ITestListener, ISuiteListener {

    /**
     * Redirect System.out → "SystemOut" Log4j2 logger at class-load time.
     * Any System.out.println(...) anywhere in the project (363 calls across 36 files)
     * is automatically captured into framework.log at DEBUG level.
     * No manual file-by-file changes required — this single redirect covers everything.
     */
    static {
        System.setOut(org.apache.logging.log4j.io.IoBuilder
                .forLogger("SystemOut")
                .setLevel(org.apache.logging.log4j.Level.DEBUG)
                .buildPrintStream());
    }

    // Carries the domain label (e.g. "AR") for @FrameworkValidation methods across
    // onTestStart → onTestSuccess/onTestFailure without creating an ExtentTest node.
    private static final ThreadLocal<String> frameworkCheckLabel = new ThreadLocal<>();

    // ── Suite-level lifecycle — fires ONCE per <suite>, not once per <test> ──────
    // RC1 fix: initReports/flushReports moved here so the static `extent` object
    // is created once and the final flush writes all four <test> contexts' results.

    @Override
    public void onStart(ISuite suite) {
        ExtentReport.initReports();
        boolean healthy;
        try {
            healthy = PreSuiteHealthCheck.run(ExtentReport.extent);
        } finally {
            // Always flush so index.html exists even when run() throws or returns false.
            ExtentReport.flushReports();
        }
        if (!healthy) {
            throw new RuntimeException(
                    "Pre-execution health check failed — suite aborted. Open index.html for details.");
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        ExtentReport.flushReports();
    }

    // ── Test-context lifecycle — fires once per <test> element ───────────────────

    @Override
    public void onStart(ITestContext context) {
        Log.info("SUITE STARTED: {}", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        Log.info("SUITE FINISHED: {}", context.getName());
    }

    // ── Per-test callbacks ────────────────────────────────────────────────────────

    @Override
    public void onTestStart(ITestResult result) {

        String methodName = result.getMethod().getMethodName();
        TestContextManager.setTestName(methodName);
        TestContextManager.setTestId();
        Log.info("TEST STARTED: {}", methodName);

        // @FrameworkValidation methods (e.g. executeRules) are pre-execution checks,
        // not test cases. Skip ExtentTest creation so they don't inflate the pass count.
        // Their outcome is recorded in the report's Environment panel via onTestSuccess/Failure.
        boolean isFrameworkValidation = result.getMethod()
                .getConstructorOrMethod().getMethod()
                .isAnnotationPresent(FrameworkValidation.class);

        if (isFrameworkValidation) {
            Object[] p = result.getParameters();
            String domain = (p.length > 0) ? String.valueOf(p[0]) : methodName;
            frameworkCheckLabel.set(domain);
            return;
        }

        Object[] params = result.getParameters();

        // Read @TestCaseId annotation (base ID, e.g. "FIN-AR-ADD")
        TestCaseId tcIdAnnotation = result.getMethod()
                .getConstructorOrMethod()
                .getMethod()
                .getAnnotation(TestCaseId.class);

        if (params.length > 0 && params[0] instanceof InvocationData data) {

            // For DB-mode AR Voucher variants, masterTestData.testcasename holds the rich
            // variant label built by buildDynamicTestCaseName() (e.g.
            // "SEL_DOC | Ref:Finance-Dup | 1SL-1Doc | Amt:FULL | Grid:EQUAL | Pmt:CHEQUE | Date:TODAY").
            // Use that as the title instead of the static @Test description.
            // Basic scenarios (FIFO, REF_SITEWISE_FIFO) set testcasename = method name
            // ("createArVoucher") — fall back to the annotation description for those.
            String richName = null;
            if (params[0] instanceof MergedFinanceArVoucherData arData
                    && arData.getMasterTestData() != null) {
                String tcName = arData.getMasterTestData().getTestcasename();
                if (tcName != null && !tcName.isBlank()
                        && !tcName.equalsIgnoreCase("createArVoucher")) {
                    richName = tcName;
                }
            }

            // Build structured test case ID: base + zero-padded invocation index
            String tcId = (tcIdAnnotation != null)
                    ? String.format("%s-%02d", tcIdAnnotation.value(), data.getInvocationIndex())
                    : null;
            TestContextManager.setTestCaseId(tcId);

            String prefix = (tcId != null) ? "[" + tcId + "] " : "";
            String title = prefix
                    + (richName != null ? richName : result.getMethod().getDescription())
                    + " [" + data.getInvocationIndex() + "/" + data.getTotalInvocations() + "]";
            ExtentReport.createTest(title);

        } else {
            // RC2 fix: non-InvocationData tests (e.g., executeRules via @Parameters) also
            // get an ExtentTest so their pass/fail is captured in the report.
            String baseId = (tcIdAnnotation != null) ? tcIdAnnotation.value() : null;
            TestContextManager.setTestCaseId(baseId);

            String prefix = (baseId != null) ? "[" + baseId + "] " : "";
            String desc = result.getMethod().getDescription();
            ExtentReport.createTest(prefix + (desc != null && !desc.isBlank() ? desc : methodName));
        }

        // Assign author
        FrameworkAnnotation fa = result.getMethod()
                .getConstructorOrMethod()
                .getMethod()
                .getAnnotation(FrameworkAnnotation.class);
        if (fa != null) {
            ExtentReport.assignAuthor(fa.value());
        }
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        String label = frameworkCheckLabel.get();
        if (label != null) {
            ExtentReport.extent.setSystemInfo("Rule Validation [" + label + "]", "PASSED");
            frameworkCheckLabel.remove();
            Log.info("FRAMEWORK CHECK PASSED: {}", label);
            return;
        }
        Log.info("TEST PASSED: {}", result.getName());
        ExtentLogger.pass(result.getName() + " is passed");
        ExtentReport.flushReports();
        TestContextManager.clear();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String label = frameworkCheckLabel.get();
        if (label != null) {
            String msg = result.getThrowable() != null ? result.getThrowable().getMessage() : "unknown";
            ExtentReport.extent.setSystemInfo("Rule Validation [" + label + "]", "FAILED: " + msg);
            frameworkCheckLabel.remove();
            Log.error("FRAMEWORK CHECK FAILED [{}]: {}", label, msg);
            return;
        }
        Log.error("TEST FAILED: {}", result.getName());
        Log.error("Failure cause: {}",
                result.getThrowable() != null ? result.getThrowable().getMessage() : "no throwable");
        ExtentLogger.fail(result.getName() + " is failed");
        ExtentLogger.fail(result.getMethod().getDescription() + " is failed");
        ExtentLogger.fail("Exception: " + result.getThrowable().getClass().getSimpleName());
        ExtentLogger.fail("Message " + result.getThrowable().getMessage());
        ExtentLogger.fail("StackTrace " + Arrays.toString(result.getThrowable().getStackTrace()));
        ExtentReport.flushReports();
        TestContextManager.clear();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        Log.warn("TEST SKIPPED: {}", result.getName());

        if (!ExtentManager.hasTest()) {
            // DataProvider threw before onTestStart — no ExtentTest exists yet.
            // Create a visible failure node so the error is not silent in the report.
            String title = "DATA PROVIDER FAILURE — " + result.getMethod().getMethodName();
            ExtentReport.createTest(title);
            Throwable t = result.getThrowable();
            String detail = (t != null) ? t.getMessage() : "DataProvider returned no data";
            ExtentLogger.fail(detail);
        } else {
            // @BeforeMethod or other config failure after onTestStart already created a node.
            Throwable t = result.getThrowable();
            ExtentLogger.warning("Test skipped" + (t != null ? ": " + t.getMessage() : ""));
        }
        ExtentReport.flushReports();
        TestContextManager.clear();
    }
}
