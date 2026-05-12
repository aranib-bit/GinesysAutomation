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
import com.gsl.reports.ArVoucherDescriptionBuilder;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ExtentReport;
import com.gsl.reports.PreSuiteHealthCheck;
import com.gsl.testdata.InvocationData;
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

    @Override
    public void onStart(ISuite suite) {
        ExtentReport.initReports();
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
        TestContextManager.setExecutionId();
        TestContextManager.setBrowser(ConfigFactory.getConfig().browser());
        TestContextManager.setEnv(ConfigFactory.getConfig().env());
        Log.info("TEST STARTED: {}", methodName);

        // @FrameworkValidation methods are pre-execution checks, not test cases.
        // Skip ExtentTest creation; their outcome is recorded via onTestSuccess/Failure.
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

        if (invData != null) {

            String richName = null;
            if (arData != null && arData.getMasterTestData() != null) {
                String tcName = arData.getMasterTestData().getTestcasename();
                if (tcName != null && !tcName.isBlank()
                        && !tcName.equalsIgnoreCase("createArVoucher")) {
                    richName = tcName;
                }
            }

            String tcId = (tcIdAnnotation != null)
                    ? String.format("%s-%02d", tcIdAnnotation.value(), invData.getInvocationIndex())
                    : null;
            TestContextManager.setTestCaseId(tcId);

            String prefix = (tcId != null) ? "[" + tcId + "] " : "";
            String title = prefix
                    + (richName != null ? richName : result.getMethod().getDescription())
                    + " [" + invData.getInvocationIndex() + "/" + invData.getTotalInvocations() + "]";
            ExtentReport.createTest(title);

            // P2: Set a human-readable sentence description on the test node
            if (arData != null) {
                ExtentReport.setTestDescription(ArVoucherDescriptionBuilder.build(arData));
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

        // Inject JS logger at test start so all console activity during the test is captured
        if (ConfigFactory.getConfig().browserLogsEnabled() && DriverManager.getDriver() != null) {
            AdvancedJsLogger.inject();
        }

        // Reset network log buffer so this test only sees its own traffic
        NetworkCaptureManager.get().clear();
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

        try {
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
            frameworkCheckLabel.remove();
            Log.error("FRAMEWORK CHECK FAILED [{}]: {}", label, msg);
            return;
        }
        Log.error("TEST FAILED: {}", result.getName());
        Log.error("Failure cause: {}",
                result.getThrowable() != null ? result.getThrowable().getMessage() : "no throwable");
        // One top-level status marker; full exception details are in the StepExecutor child node.
        ExtentLogger.fail(result.getName() + " FAILED");

        try {
            FailureBundleOrchestrator.onFailure(result).attachToReport();
        } catch (Exception e) {
            Log.warn("[TestListener] Diagnostic report attachment failed on failure: {}", e.getMessage());
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
