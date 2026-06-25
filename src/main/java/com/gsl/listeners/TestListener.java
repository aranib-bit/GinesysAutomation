package com.gsl.listeners;

import com.gsl.constants.FrameworkConstants;
import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.annotations.FrameworkValidation;
import com.gsl.annotations.TestCaseId;
import com.gsl.enums.AppOperation;
import com.gsl.enums.ISubModule;
import com.gsl.enums.SubModuleResolver;
import com.gsl.pages.enums.Finance.Operations.OperationsMenuType;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;
import com.gsl.browser.AdvancedJsLogger;
import com.gsl.browser.AdvancedLogFetcher;
import com.gsl.browser.BrowserLogRecord;
import com.gsl.browser.LogAttachment;
import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.DiagnosticReport;
import com.gsl.diagnostics.FailureBundleOrchestrator;
import com.gsl.diagnostics.RootCause;
import com.gsl.diagnostics.RootCauseClassifier;
import com.gsl.diagnostics.RootCauseInputs;
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
import com.gsl.reports.FinanceDomainReporter;
import com.gsl.reports.SummaryReportBuilder;
import com.gsl.reports.PopupTrailBuffer;
import com.gsl.reports.contract.DomainReporter;
import com.gsl.reports.contract.DomainReporterRegistry;
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
import java.util.Optional;
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
        DomainReporterRegistry.register(new FinanceDomainReporter());
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
        SummaryReportBuilder.build();
        ExtentReport.flushReports();
        copyToLatest();
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
        if (SuiteCircuitBreaker.isTripped()) {
            throw new org.testng.SkipException("Suite circuit breaker tripped — skipping remaining tests.");
        }

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
        PopupTrailBuffer.clear();
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

        Optional<DomainReporter> domainReporter = DomainReporterRegistry.resolve(params);
        Object domainData = domainReporter.map(r -> r.extractTestData(params)).orElse(null);

        if (invData != null) {

            String richName = (domainReporter.isPresent() && domainData != null)
                    ? domainReporter.get().buildRichName(domainData) : null;

            String tcId = (tcIdAnnotation != null)
                    ? String.format("%s-%02d", tcIdAnnotation.value(), invData.getInvocationIndex())
                    : null;
            TestContextManager.setTestCaseId(tcId);

            String prefix = (tcId != null) ? "[" + tcId + "] " : "";

            // Title: human-readable sentence after the ID; technical params go to description
            String sentence = (domainReporter.isPresent() && domainData != null)
                    ? domainReporter.get().buildSentence(domainData) : null;
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

            // R-02: attach the shard node for this test (main node already created above)
            MainModuleType module    = fa.module();
            AppOperation   operation = fa.operation();
            ISubModule     subModule;
            try {
                subModule = SubModuleResolver.resolve(module, fa.subModule());
            } catch (IllegalArgumentException e) {
                Log.warn("[TestListener] Unknown subModule '{}' — summary-only reporting", fa.subModule());
                subModule = OperationsMenuType.ARVOUCHER;
            }

            String currentTitle = ExtentManager.getExtentTest() != null
                    ? ExtentManager.getExtentTest().getModel().getName()
                    : result.getMethod().getMethodName();
            ExtentReport.createTest(currentTitle, module, subModule, operation);
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
        if (ConfigFactory.getNetworkConfig().browserLogsEnabled() && DriverManager.getDriver() != null) {
            AdvancedJsLogger.inject();
            AdvancedJsLogger.clearLogs();
        }

        // Reset network log buffer so this test only sees its own traffic
        NetworkCaptureManager.get().clear();
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        SuiteCircuitBreaker.recordPass();
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

        Optional<DomainReporter> successReporter = DomainReporterRegistry.resolve(result.getParameters());
        try {
            ActionTrailBuffer.record("Actual Results");
            successReporter.ifPresent(r -> r.onTestSuccess(r.getAccumulator()));
        } catch (Exception e) {
            Log.warn("[TestListener] Actual Results flush failed on success: {}", e.getMessage());
        }

        try {
            ActionTrailBuffer.record("Logs & Diagnostics");
            FailureBundleOrchestrator.onSuccess(result).attachToReport();
        } catch (Exception e) {
            Log.warn("[TestListener] Diagnostic report attachment failed on success: {}", e.getMessage());
        }

        ExtentReport.recordTestResult(result, "PASS");

        // Force PASS status on the node — ExtentReports uses highest-severity log entry
        // (FAIL > PASS), so AOP fail() entries from fluent-wait retry-throws on internally
        // recovered exceptions would otherwise make a passing test appear red in the report.
        com.aventstack.extentreports.ExtentTest extentNode = ExtentManager.getExtentTest();
        if (extentNode != null) {
            extentNode.getModel().setStatus(com.aventstack.extentreports.Status.PASS);
        }
        com.aventstack.extentreports.ExtentTest shardNode = ExtentManager.getShardTest();
        if (shardNode != null) {
            shardNode.getModel().setStatus(com.aventstack.extentreports.Status.PASS);
        }

        TestContextManager.clear();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        SuiteCircuitBreaker.recordFail(result.getTestContext().getSuite());
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

        Optional<DomainReporter> failureReporter = DomainReporterRegistry.resolve(result.getParameters());
        try {
            ActionTrailBuffer.record("Actual Results");
            failureReporter.ifPresent(r -> r.onTestFailure(r.getAccumulator()));
        } catch (Exception e) {
            Log.warn("[TestListener] Actual Results flush failed on failure: {}", e.getMessage());
        }

        // Compact failure summary — exception, last step, environment fingerprint.
        // R-04: classify the root cause first and render it as a banner inside the summary box,
        // so it's the first thing visible on scroll. Rendered before the full diagnostic bundle.
        try {
            RootCauseInputs inputs = RootCauseInputs.collect(result);
            RootCause rootCause = RootCauseClassifier.classify(inputs);
            Log.info("[RCA] [RootCause] Classified: {} | Test: {}",
                    rootCause.category().label(), result.getName());
            FailureSummaryRenderer.render(result, rootCause);
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

        ExtentReport.recordTestResult(result, "FAIL");
        TestContextManager.clear();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        Log.warn("TEST SKIPPED: {}", result.getName());

        Throwable t = result.getThrowable();
        boolean dataSkip = t instanceof org.testng.SkipException;
        if (!ExtentManager.hasTest()) {
            // No node yet (skip surfaced at DataProvider time). A SkipException is a clean
            // data-availability skip → amber; anything else is a genuine DataProvider failure → red.
            String title = (dataSkip ? "SKIPPED — " : "DATA PROVIDER FAILURE — ")
                    + result.getMethod().getMethodName();
            ExtentReport.createTest(title);
            String detail = (t != null) ? t.getMessage() : "DataProvider returned no data";
            if (dataSkip) {
                ExtentLogger.skip(detail);
            } else {
                ExtentLogger.fail(detail);
            }
        } else {
            // Use an explicit SKIP entry (amber) rather than a WARNING so the node status reads as Skip.
            ExtentLogger.skip("Test skipped" + (t != null ? ": " + t.getMessage() : ""));
        }
        ExtentReport.recordTestResult(result, "SKIP");
        TestContextManager.clear();
    }

    // ── Phase 0: copy run report to reports/latest/ for CI pickup ──────────────

    private static void copyToLatest() {
        try {
            java.nio.file.Path runDir    = java.nio.file.Paths.get(FrameworkConstants.getRunReportDir());
            java.nio.file.Path latestDir = java.nio.file.Paths.get(FrameworkConstants.getLatestReportDir());
            java.nio.file.Files.createDirectories(latestDir);
            java.nio.file.Files.copy(
                runDir.resolve("index.html"),
                latestDir.resolve("index.html"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            Log.info("[Phase0] Latest report copied to {}", latestDir.resolve("index.html"));
        } catch (Exception e) {
            Log.warn("[Phase0] Could not copy report to latest/: {}", e.getMessage());
        }
    }
}
