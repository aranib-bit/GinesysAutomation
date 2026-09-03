package com.gsl.listeners;

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
import com.gsl.reports.ReportLogger;
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
        Log.logHeap("suite-start:" + suite.getName());
        DomainReporterRegistry.register(new FinanceDomainReporter());
        ExtentReport.initReports();

        // Build the runtime profile ONCE — frozen for the entire suite.
        // Done here (ISuiteListener.onStart) so config files are read before any
        // <test> block starts and any mid-run file changes are ignored.
        RuntimeExecutionProfile profile = RuntimeProfileBuilder.build();
        RuntimeProfileHolder.set(profile);

        // Created here — the one suite-lifecycle point guaranteed to run before any
        // <test> block starts — so this node is always first in the Extent report,
        // ahead of Rule Validation and every other test.
        ExtentReport.createTest("Execution Configuration Snapshot");
        ReportConfigRenderer.renderAllSections(profile);

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
        SummaryReportBuilder.build(suite);
        // flushReports() also republishes reports/latest/index.html (see ExtentReport) —
        // no separate copyToLatest() call needed here anymore.
        ExtentReport.flushReports();
        Log.logHeap("suite-finish:" + suite.getName());
    }

    // ── Test-context lifecycle — fires once per <test> element ───────────────────

    @Override
    public void onStart(ITestContext context) {
        Log.info("SUITE STARTED: {}", context.getName());
        ReportLogger.framework("TEST CONTEXT STARTED: {}", context.getName());
    }

    @Override
    public void onFinish(ITestContext context) {
        Log.info("SUITE FINISHED: {}", context.getName());
        ReportLogger.framework("TEST CONTEXT FINISHED: {}", context.getName());

        // End-of-block session teardown: SessionPool owns every session now, so one call logs
        // out and closes them ALL — each idle session is bound to this thread in turn, given a
        // REAL application logout when healthy, then quit, its user released. Nothing is
        // thread-resident anymore, so this reaches every session regardless of which worker
        // threads ran the rows (the ThreadLocal-registry drain this replaces could not do the
        // logout and existed only because sessions used to live in per-thread state).
        com.gsl.driver.SessionPool.drainAll("end of <test> block: " + context.getName());
        com.gsl.driver.ExecutionContext.clear();
    }

    // ── Per-test callbacks ────────────────────────────────────────────────────────

    @Override
    public void onTestStart(ITestResult result) {
        if (SuiteCircuitBreaker.isTripped()) {
            throw new org.testng.SkipException("Suite circuit breaker tripped — skipping remaining tests.");
        }

        // Captured before RetryHistoryBuffer.clear() below — RetryAnalyzer.retry() records
        // the previous attempt's failure to this buffer before TestNG re-invokes onTestStart
        // for the retry, so a non-empty buffer here means this invocation is a retry attempt.
        boolean isRetry = RetryHistoryBuffer.totalAttempts() > 0;

        String methodName = result.getMethod().getMethodName();
        TestContextManager.setTestName(methodName);
        TestContextManager.setTestId();
        TestContextManager.setExecutionId();
        TestContextManager.setBrowser(ConfigFactory.getConfig().browser());
        TestContextManager.setEnv(ConfigFactory.getConfig().env());
        com.gsl.reports.ActionTrailBuffer.clear();
        StepDurationBuffer.clear();
        WaitDurationBuffer.startNewTest();   // preserves @BeforeMethod/login waits as "[pre-test]" instead of dropping them
        RetryHistoryBuffer.clear();
        PopupTrailBuffer.clear();
        Log.info("TEST STARTED: {}", methodName);
        ReportLogger.framework("TEST STARTED: {} | thread={}", methodName, Thread.currentThread().getName());

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
            if (isRetry) {
                ExtentManager.getExtentTest().assignCategory("RETRY");
            }
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

        String richTitle; // full per-row Extent title — reused below to upgrade the SessionPool
                           // borrower label from setUp's generic "ClassName.methodName"

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
            richTitle = title;

            // Description: technical parameter string (SEL_DOC | [Bank GL] | ...)
            if (richName != null) {
                ExtentReport.setTestDescription(richName);
            }

        } else {
            String baseId = (tcIdAnnotation != null) ? tcIdAnnotation.value() : null;
            TestContextManager.setTestCaseId(baseId);

            String prefix = (baseId != null) ? "[" + baseId + "] " : "";
            String sentence = (domainReporter.isPresent() && domainData != null)
                    ? domainReporter.get().buildSentence(domainData) : null;
            String desc = result.getMethod().getDescription();
            String titleText = (sentence != null && !sentence.isBlank()) ? sentence
                    : (desc != null && !desc.isBlank() ? desc : methodName);
            richTitle = prefix + titleText;
            ExtentReport.createTest(richTitle);
        }

        // Upgrade the SessionPool borrower label (generic "ClassName.methodName" from
        // WebTest.setUp, which runs before this tcId/description is known) to the rich title, so
        // the NEXT test to borrow this session reports a real description as "previous test on
        // this session" instead of just the method name. Defensive try/catch: this is diagnostic
        // metadata only — a failure here must never abort the rest of onTestStart (Extent node
        // creation, category assignment, correlation logging all follow this line).
        try {
            com.gsl.driver.SessionPool.updateCurrentLabel(richTitle);
        } catch (Exception e) {
            Log.warn("[TestListener] SessionPool.updateCurrentLabel failed for '{}': {}", richTitle, e.getMessage());
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

        // Login user (abbre) — correlates this test's execution to the pooled browser/session that
        // ran it, and to the abbre→ecode chain DB validation uses. Tag it as an Extent category
        // too (same convention as APVoucherDbValidator/ARVoucherDbValidator's "DB Validation" tag)
        // so the report's category filter can group/find every test a given login user ran.
        com.gsl.testdata.UserCredential currentUser = com.gsl.driver.DriverManager.getCurrentUser();
        String username = currentUser != null ? currentUser.username() : "unknown";
        String sessionLabel = com.gsl.driver.SessionPool.currentSessionLabel();
        String previousTestCase = com.gsl.driver.SessionPool.currentSessionPreviousLabel();
        if (ExtentManager.hasTest()) {
            ExtentManager.getExtentTest().assignCategory("User: " + username);
            if (isRetry) {
                ExtentManager.getExtentTest().assignCategory("RETRY");
            }
        }

        // Correlation block — visible in Extent HTML alongside test steps
        ExtentLogger.info(String.format(
                "executionId=%s | testId=%s | browser=%s | env=%s | thread=%s | user=%s | "
                        + "session=%s | previousTestCase=%s",
                TestContextManager.getExecutionId(),
                TestContextManager.getTestCaseId(),
                ConfigFactory.getConfig().browser(),
                ConfigFactory.getConfig().env(),
                Thread.currentThread().getName(),
                username,
                sessionLabel != null ? sessionLabel : "n/a",
                previousTestCase != null ? previousTestCase : "n/a"));

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
        ReportLogger.framework("TEST PASSED: {}", result.getName());
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
        ExtentManager.clearAll();
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
        ReportLogger.frameworkError("TEST FAILED: {} — {}",
                result.getName(),
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
        ExtentManager.clearAll();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        Log.warn("TEST SKIPPED: {}", result.getName());
        ReportLogger.frameworkWarn("TEST SKIPPED: {}", result.getName());

        Throwable t = result.getThrowable();
        boolean dataSkip = t instanceof org.testng.SkipException;
        if (!ExtentManager.hasTest()) {
            // No node yet — circuit breaker threw before onTestStart created a node, or
            // the skip surfaced at DataProvider time. Build a proper title from annotations.
            TestCaseId skipTcId = result.getMethod().getConstructorOrMethod().getMethod()
                    .getAnnotation(TestCaseId.class);
            String skipPrefix = (skipTcId != null) ? "[" + skipTcId.value() + "] " : "";
            String skipDesc   = result.getMethod().getDescription();
            String skipLabel  = (skipDesc != null && !skipDesc.isBlank())
                    ? skipDesc : result.getMethod().getMethodName();
            Object[] skipParams = result.getParameters();
            InvocationData skipInvData = (skipParams != null)
                    ? Arrays.stream(skipParams)
                            .filter(p -> p instanceof InvocationData)
                            .map(p -> (InvocationData) p)
                            .findFirst().orElse(null)
                    : null;
            String indexSuffix = (skipInvData != null)
                    ? " [" + skipInvData.getInvocationIndex() + "/" + skipInvData.getTotalInvocations() + "]"
                    : "";
            String title = (dataSkip ? "" : "DATA PROVIDER FAILURE — ")
                    + skipPrefix + skipLabel + indexSuffix;
            ExtentReport.createTest(title);
            String detail = (t != null) ? t.getMessage() : "DataProvider returned no data";
            if (dataSkip) {
                ExtentLogger.skip(detail);
            } else {
                ExtentLogger.fail(detail);
            }
        } else {
            // Parallel DataSupplier race: onTestStart may have run before parameters were bound
            // (result.getParameters() returns [] at that moment), so the node got a generic title.
            // By onTestSkipped time, parameters are always bound — try to enrich the title now.
            Object[] lateParams = result.getParameters();
            if (lateParams != null && lateParams.length > 0) {
                Optional<DomainReporter> lateReporter = DomainReporterRegistry.resolve(lateParams);
                if (lateReporter.isPresent()) {
                    Object lateData = lateReporter.get().extractTestData(lateParams);
                    InvocationData lateInvData = Arrays.stream(lateParams)
                            .filter(p -> p instanceof InvocationData)
                            .map(p -> (InvocationData) p)
                            .findFirst().orElse(null);
                    if (lateData != null && lateInvData != null) {
                        String lateSentence = lateReporter.get().buildSentence(lateData);
                        if (lateSentence != null && !lateSentence.isBlank()) {
                            TestCaseId lateId = result.getMethod().getConstructorOrMethod()
                                    .getMethod().getAnnotation(TestCaseId.class);
                            String lateTcId = (lateId != null)
                                    ? String.format("%s-%02d", lateId.value(), lateInvData.getInvocationIndex())
                                    : null;
                            String latePrefix = (lateTcId != null) ? "[" + lateTcId + "] " : "";
                            String enrichedTitle = latePrefix + lateSentence
                                    + " [" + lateInvData.getInvocationIndex()
                                    + "/" + lateInvData.getTotalInvocations() + "]";
                            if (ExtentManager.getExtentTest() != null)
                                ExtentManager.getExtentTest().getModel().setName(enrichedTitle);
                            if (ExtentManager.getShardTest() != null)
                                ExtentManager.getShardTest().getModel().setName(enrichedTitle);
                        }
                    }
                }
            }
            // Use an explicit SKIP entry (amber) rather than a WARNING so the node status reads as Skip.
            // Truncate Selenium session diagnostic noise — take only the first line of the message.
            String skipReason = "";
            if (t != null) {
                String msg = t.getMessage();
                if (msg != null) {
                    String firstLine = msg.split("\\n")[0].trim();
                    int hostIdx = firstLine.indexOf(" Host info:");
                    if (hostIdx > 0) firstLine = firstLine.substring(0, hostIdx).trim();
                    int buildIdx = firstLine.indexOf(" Build info:");
                    if (buildIdx > 0) firstLine = firstLine.substring(0, buildIdx).trim();
                    skipReason = ": " + firstLine;
                }
            }
            ExtentLogger.skip("Test skipped" + skipReason);
        }
        ExtentReport.recordTestResult(result, "SKIP");
        TestContextManager.clear();
        ExtentManager.clearAll();
    }

}
