package com.gsl.listeners;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.FrameworkValidation;
import com.gsl.annotations.TestCaseId;
import com.gsl.browser.AdvancedJsLogger;
import com.gsl.browser.AdvancedLogFetcher;
import com.gsl.browser.BrowserLogRecord;
import com.gsl.browser.LogAttachment;
import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.DiagnosticReport;
import com.gsl.diagnostics.FailureBundleOrchestrator;
import com.gsl.diagnostics.section.BrowserLogSection;
import com.gsl.diagnostics.section.EventLogSection;
import com.gsl.diagnostics.section.HtmlCaptureSection;
import com.gsl.diagnostics.section.NetworkLogSection;
import com.gsl.diagnostics.section.RemoteLogSection;
import com.gsl.diagnostics.section.ServerRole;
import com.gsl.diagnostics.section.ServerUtilSection;
import com.gsl.driver.DriverManager;
import com.gsl.network.NetworkCaptureManager;
import com.gsl.network.NetworkLogAttachment;
import com.gsl.remote.exception.RemoteCommandException;
import com.gsl.remote.fetcher.SshLogFetcher;
import com.gsl.remote.filter.KeywordTimeWindowFilter;
import com.gsl.remote.model.CaptureMode;
import com.gsl.remote.model.Credentials;
import com.gsl.remote.model.LogFetchRequest;
import com.gsl.remote.model.LogFetchResponse;
import com.gsl.remote.model.OSType;
import com.gsl.remote.parser.RegexLogParser;
import com.gsl.remote.retry.RetryPolicy;
import com.gsl.reports.ActionTrailBuffer;
import com.gsl.reports.ApVoucherDescriptionBuilder;
import com.gsl.reports.ArVoucherDescriptionBuilder;
import com.gsl.reports.RetryHistoryBuffer;
import com.gsl.reports.StepDurationBuffer;
import com.gsl.reports.WaitDurationBuffer;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ExtentReport;
import com.gsl.reports.FailureSummaryRenderer;
import com.gsl.reports.PreSuiteHealthCheck;
import com.gsl.reports.ReportConfigRenderer;
import com.gsl.reports.RuntimeProfileBuilder;
import com.gsl.reports.RuntimeProfileHolder;
import com.gsl.reports.profile.RuntimeExecutionProfile;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.TestContextManager;

import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    // Tracks how many validation <test> blocks remain; snapshot is created when the
    // last one finishes so it appears immediately after rule-validation in the report.
    private final AtomicInteger pendingValidationContexts = new AtomicInteger(0);
    private final AtomicBoolean snapshotCreated          = new AtomicBoolean(false);

    // ── Suite-level lifecycle — fires ONCE per <suite>, not once per <test> ──────

    @Override
    public void onStart(ISuite suite) {
        Log.logHeap("suite-start:" + suite.getName());
        ExtentReport.initReports();

        // Build the runtime profile ONCE — frozen for the entire suite.
        // Done here (ISuiteListener.onStart) so config files are read before any
        // <test> block starts and any mid-run file changes are ignored.
        RuntimeExecutionProfile profile = RuntimeProfileBuilder.build();
        RuntimeProfileHolder.set(profile);

        // Count <test> blocks whose name contains "validation" (case-insensitive).
        // The snapshot node is created after ALL of them finish, so it appears
        // immediately after rule-validation in the Extent report rather than at the end.
        int validationCount = (int) suite.getXmlSuite().getTests().stream()
                .filter(t -> t.getName().toLowerCase().contains("validation"))
                .count();
        pendingValidationContexts.set(validationCount);

        ReportConfigRenderer.renderDashboard(ExtentReport.getExtentReports(), profile);
        try {
            String snapshotPath = ReportConfigRenderer.generateJsonSnapshot(profile);
            Log.info("[ReportConfig] Runtime snapshot written to: {}", snapshotPath);
        } catch (Exception e) {
            Log.warn("[ReportConfig] Snapshot generation failed: {}", e.getMessage());
        }

        boolean healthy;
        try {
            healthy = PreSuiteHealthCheck.run(ExtentReport.extent);
        } finally {
            ExtentReport.flushReports();
        }
        if (!healthy) {
            throw new RuntimeException(
                    "Pre-execution health check failed — suite aborted. Open index.html for details.");
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        // Fallback: if no validation <test> blocks existed in this suite file
        // (e.g. testng-regression.xml), create the snapshot now so it is never missing.
        if (snapshotCreated.compareAndSet(false, true)) {
            RuntimeExecutionProfile profile = RuntimeProfileHolder.get();
            if (profile != null) {
                ExtentReport.createTest("Execution Configuration Snapshot");
                ReportConfigRenderer.renderAllSections(profile);
            }
        }
        ExtentReport.flushReports();
        Log.logHeap("suite-finish:" + suite.getName());
    }

    // ── Test-context lifecycle — fires once per <test> element ───────────────────

    @Override
    public void onStart(ITestContext context) {
        Log.info("SUITE STARTED: {}", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        Log.info("SUITE FINISHED: {}", context.getName());

        // When the last validation <test> block finishes, inject the snapshot node.
        // Validation tests complete in milliseconds while Selenium tests are still
        // launching browsers, so the node lands right after rule-validation in the report.
        if (context.getName().toLowerCase().contains("validation")
                && pendingValidationContexts.decrementAndGet() == 0
                && snapshotCreated.compareAndSet(false, true)) {
            RuntimeExecutionProfile profile = RuntimeProfileHolder.get();
            if (profile != null) {
                ExtentReport.createTest("Execution Configuration Snapshot");
                ReportConfigRenderer.renderAllSections(profile);
                ExtentReport.flushReports();
            }
        }
    }

    // ── Per-test callbacks ────────────────────────────────────────────────────────

    @Override
    public void onTestStart(ITestResult result) {

        String methodName = result.getMethod().getMethodName();
        TestContextManager.setTestName(methodName);
        TestContextManager.setTestId();
        TestContextManager.setExecutionId();
        TestContextManager.setBrowser(ConfigFactory.getConfig().browser());
        TestContextManager.setEnv(ConfigFactory.getConfig().env());
        com.gsl.reports.ActionTrailBuffer.clear();
        StepDurationBuffer.clear();
        WaitDurationBuffer.clear();
        RetryHistoryBuffer.clear();
        Log.info("TEST STARTED: {}", methodName);

        // @FrameworkValidation methods are pre-execution validation checks.
        // Create a visible ExtentTest node so they appear in the dashboard count,
        // then return early (pass/fail is logged via onTestSuccess/Failure).
        boolean isFrameworkValidation = result.getMethod()
                .getConstructorOrMethod().getMethod()
                .isAnnotationPresent(FrameworkValidation.class);

        if (isFrameworkValidation) {
            Object[] p = result.getParameters();
            String domain   = (p.length > 0) ? String.valueOf(p[0]) : methodName;
            String execType = (p.length > 1) ? String.valueOf(p[1]) : "";
            frameworkCheckLabel.set(domain);
            // Create a visible ExtentTest node (outcome logged via onTestSuccess/Failure)
            String title = "Rule Validation [" + domain + "]"
                    + (execType.isBlank() ? "" : " — " + execType);
            ExtentReport.createTest(title);
            return;
        }

        Object[] params = result.getParameters();

        TestCaseId tcIdAnnotation = result.getMethod()
                .getConstructorOrMethod()
                .getMethod()
                .getAnnotation(TestCaseId.class);

        // P6 fix: params[0] is the UserCredential pool-slot (null at test-start), so
        // params[0] instanceof InvocationData is always false. Scan all params instead.
        InvocationData invData = Arrays.stream(params)
                .filter(p -> p instanceof InvocationData)
                .map(p -> (InvocationData) p)
                .findFirst()
                .orElse(null);
        MergedFinanceArVoucherData arData = Arrays.stream(params)
                .filter(p -> p instanceof MergedFinanceArVoucherData)
                .map(p -> (MergedFinanceArVoucherData) p)
                .findFirst()
                .orElse(null);
        MergedFinanceApVoucherData apData = Arrays.stream(params)
                .filter(p -> p instanceof MergedFinanceApVoucherData)
                .map(p -> (MergedFinanceApVoucherData) p)
                .findFirst()
                .orElse(null);

        if (invData != null) {

            String richName = null;
            if (arData != null && arData.getMasterTestData() != null) {
                String tcName = arData.getMasterTestData().getTestcasename();
                if (tcName != null && !tcName.isBlank()
                        && !tcName.equalsIgnoreCase("createArVoucher")) {
                    richName = tcName;
                }
            } else if (apData != null && apData.getMasterTestData() != null) {
                String tcName = apData.getMasterTestData().getTestcasename();
                if (tcName != null && !tcName.isBlank()
                        && !tcName.equalsIgnoreCase("createApVoucherAdvancePayment")
                        && !tcName.equalsIgnoreCase("createApVoucherDocumentPayment")) {
                    richName = tcName;
                }
            }

            String tcId = (tcIdAnnotation != null)
                    ? String.format("%s-%02d", tcIdAnnotation.value(), invData.getInvocationIndex())
                    : null;
            TestContextManager.setTestCaseId(tcId);

            String prefix = (tcId != null) ? "[" + tcId + "] " : "";

            // Title: human-readable sentence after the ID; technical params go to description
            String sentence = (arData != null) ? ArVoucherDescriptionBuilder.build(arData)
                            : (apData != null) ? ApVoucherDescriptionBuilder.build(apData)
                            : null;
            String titleText = (sentence != null && !sentence.isBlank())
                    ? sentence
                    : (richName != null ? richName : result.getMethod().getDescription());
            String title = prefix + titleText
                    + " [" + invData.getInvocationIndex() + "/" + invData.getTotalInvocations() + "]";
            ExtentReport.createTest(title);

            // Description: technical parameter string (SEL_DOC | [Bank GL] | ...)
            if (richName != null) {
                ExtentReport.setTestDescription(richName);
            }

        } else {
            String baseId = (tcIdAnnotation != null) ? tcIdAnnotation.value() : null;
            TestContextManager.setTestCaseId(baseId);

            String prefix = (baseId != null) ? "[" + baseId + "] " : "";
            String desc = result.getMethod().getDescription();
            ExtentReport.createTest(prefix + (desc != null && !desc.isBlank() ? desc : methodName));
        }

        FrameworkAnnotation fa = result.getMethod()
                .getConstructorOrMethod()
                .getMethod()
                .getAnnotation(FrameworkAnnotation.class);
        if (fa != null) {
            ExtentReport.assignAuthor(fa.value());
        }

        // Correlation block — visible in Extent HTML alongside test steps
        ExtentLogger.info(String.format(
                "executionId=%s | testId=%s | browser=%s | env=%s | thread=%s",
                TestContextManager.getExecutionId(),
                TestContextManager.getTestCaseId(),
                ConfigFactory.getConfig().browser(),
                ConfigFactory.getConfig().env(),
                Thread.currentThread().getName()));

        // Inject JS logger and reset log buffers so this test sees only its own activity
        if (ConfigFactory.getConfig().browserLogsEnabled() && DriverManager.getDriver() != null) {
            AdvancedJsLogger.inject();
            AdvancedJsLogger.clearLogs();
        }

        // Reset network log buffer so this test only sees its own traffic
        NetworkCaptureManager.get().clear();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        String label = frameworkCheckLabel.get();
        if (label != null) {
            ExtentReport.extent.setSystemInfo("Rule Validation [" + label + "]", "PASSED");
            ExtentLogger.pass("All validation rules passed for domain: " + label);
            frameworkCheckLabel.remove();
            Log.info("FRAMEWORK CHECK PASSED: {}", label);
            ExtentReport.flushReports();
            TestContextManager.clear();
            return;
        }
        Log.info("TEST PASSED: {}", result.getName());
        ExtentLogger.pass(result.getName() + " is passed");

        try {
            ActionTrailBuffer.record("Logs & Diagnostics");
            FailureBundleOrchestrator.onSuccess(result).attachToReport();
        } catch (Exception e) {
            Log.warn("[TestListener] Diagnostic report attachment failed on success: {}", e.getMessage());
        }

        ExtentReport.flushReports();
        TestContextManager.clear();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String label = frameworkCheckLabel.get();
        if (label != null) {
            String msg = result.getThrowable() != null ? result.getThrowable().getMessage() : "unknown";
            ExtentReport.extent.setSystemInfo("Rule Validation [" + label + "]", "FAILED: " + msg);
            ExtentLogger.fail("Validation rules FAILED for domain: " + label + " — " + msg);
            frameworkCheckLabel.remove();
            Log.error("FRAMEWORK CHECK FAILED [{}]: {}", label, msg);
            ExtentReport.flushReports();
            TestContextManager.clear();
            return;
        }
        Log.error("TEST FAILED: {}", result.getName());
        Log.error("Failure cause: {}",
                result.getThrowable() != null ? result.getThrowable().getMessage() : "no throwable");
        // One top-level status marker; full exception details are in the StepExecutor child node.
        ExtentLogger.fail(result.getName() + " FAILED");

        // Compact failure summary — exception, last step, environment fingerprint.
        // Rendered before the full diagnostic bundle so it's the first thing visible on scroll.
        try {
            FailureSummaryRenderer.render(result);
        } catch (Exception summaryEx) {
            Log.warn("[TestListener] Failure summary render failed: {}", summaryEx.getMessage());
        }

        try {
            ActionTrailBuffer.record("Logs & Diagnostics");
            FailureBundleOrchestrator.onFailure(result).attachToReport();
        } catch (Exception e) {
            Log.warn("[TestListener] Diagnostic report attachment failed on failure: {}", e.getMessage());
        }

        String snapshotPath = ReportConfigRenderer.getSnapshotPath();
        if (snapshotPath != null) {
            ExtentLogger.info("<b>Runtime Snapshot:</b> " + snapshotPath);
        }

        ExtentReport.flushReports();
        TestContextManager.clear();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        Log.warn("TEST SKIPPED: {}", result.getName());

        if (!ExtentManager.hasTest()) {
            String title = "DATA PROVIDER FAILURE — " + result.getMethod().getMethodName();
            ExtentReport.createTest(title);
            Throwable t = result.getThrowable();
            String detail = (t != null) ? t.getMessage() : "DataProvider returned no data";
            ExtentLogger.fail(detail);
        } else {
            Throwable t = result.getThrowable();
            ExtentLogger.warning("Test skipped" + (t != null ? ": " + t.getMessage() : ""));
        }
        ExtentReport.flushReports();
        TestContextManager.clear();
    }
}
