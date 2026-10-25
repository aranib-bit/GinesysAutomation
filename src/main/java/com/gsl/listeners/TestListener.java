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
import com.gsl.reports.LogArchiver;
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
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserPoolStatusRow;
import com.gsl.testdata.UserType;
import com.gsl.reports.FoldableSectionRenderer;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.ScreenshotPolicy;
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

/**
 * Central TestNG lifecycle listener — the single place where suite/test start-finish/pass/fail/skip
 * events are translated into ExtentReport nodes, diagnostic-bundle attachments, and framework-wide
 * bookkeeping (session pool draining, System.out capture, {@link SuiteCircuitBreaker} tallies, etc.).
 *
 * <p>Registered once per suite via {@code testng.xml}. Implements both {@link ISuiteListener}
 * (once-per-{@code <suite>} hooks) and {@link ITestListener} (once-per-{@code <test>} and
 * once-per-test-method hooks) — the two lifecycles are kept in clearly separated sections below
 * since they fire at very different granularities and are easy to confuse.
 *
 * <p>Because almost every cross-cutting framework concern (reporting, retries, root-cause
 * classification, browser/network log capture, user-pool status) is wired in from here, this class
 * is a good starting point for understanding "what happens around every test" — but changes to it
 * affect literally every test in the suite, so read a method's full body (and its neighbors) before
 * touching it. Key collaborators:
 * <ul>
 *   <li>{@link SuiteCircuitBreaker} — suite-abort tally, consulted in {@link #onTestStart(ITestResult)}</li>
 *   <li>{@link com.gsl.reports.contract.DomainReporterRegistry} — resolves the right
 *       {@link com.gsl.reports.contract.DomainReporter} for a test's parameters, used to build
 *       rich Extent titles/descriptions and to flush per-domain "Actual Results"</li>
 *   <li>{@link com.gsl.diagnostics.FailureBundleOrchestrator} — attaches the full diagnostic bundle
 *       (browser logs, network logs, screenshots, remote logs, root-cause classification) on
 *       success and failure</li>
 *   <li>{@link com.gsl.driver.SessionPool} — session borrower-label bookkeeping and end-of-{@code
 *       <test>} teardown ({@link #onFinish(ITestContext)})</li>
 * </ul>
 */
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

    /**
     * Fires once per {@code <suite>}, before any {@code <test>} block starts. Archives the previous
     * run's log offsets, registers {@link FinanceDomainReporter}, initializes the Extent report,
     * freezes the {@link RuntimeExecutionProfile} for the whole suite, renders the "Execution
     * Configuration Snapshot" node (always first in the report), and runs
     * {@link PreSuiteHealthCheck}.
     *
     * @throws RuntimeException if the pre-execution health check fails — aborts the suite before
     *                          any test runs rather than letting every test fail against a broken environment
     */
    @Override
    public void onStart(ISuite suite) {
        Log.logHeap("suite-start:" + suite.getName());
        logBuildFingerprint();
        // Snapshots each live log file's current size before this run writes a single line — see
        // LogArchiver's own Javadoc for why this offset-based approach replaced relying on Log4j2's
        // own rollover to scope reports/runs/<runId>/logs.zip to just this run.
        LogArchiver.markRunStart();
        DomainReporterRegistry.register(new FinanceDomainReporter());
        beginCoverageRun(suite);
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

    /** Group/tag filters resolved at suite start, read at suite finish by the coverage workbook. */
    private static volatile com.gsl.utils.dataprovider.fixture.finance.ActiveGroupFilters coverageFilters =
            com.gsl.utils.dataprovider.fixture.finance.ActiveGroupFilters.none();

    /**
     * Records run-level facts (env, browser, start time, active {@code *.group}/{@code *.tag}
     * filters read from system properties and the suite's XML parameters) for the coverage
     * workbook. Failure-safe: a problem here must never affect the suite.
     */
    private static void beginCoverageRun(ISuite suite) {
        try {
            java.util.Map<String, String> xml = new java.util.HashMap<>(suite.getXmlSuite().getAllParameters());
            for (org.testng.xml.XmlTest t : suite.getXmlSuite().getTests()) {
                t.getAllParameters().forEach(xml::putIfAbsent);
            }
            coverageFilters = com.gsl.utils.dataprovider.fixture.finance.ActiveGroupFilters.resolve(xml::get);
            String file = suite.getXmlSuite().getFileName();
            String fileName = file == null ? "" : java.nio.file.Paths.get(file).getFileName().toString();
            com.gsl.reports.RunMeta.begin(suite.getName(), fileName, ConfigFactory.getConfig().env(),
                    com.gsl.config.BrowserResolver.name(), coverageFilters.describe());
        } catch (Throwable t) {
            Log.warn("[CoverageWorkbook] Run metadata unavailable: {}", t.toString());
        }
    }

    /**
     * Round 24 — logs when the currently-loaded classes were actually compiled, so a "did my fix
     * really run?" question can be answered by reading one log line instead of manual `.class`
     * timestamp archaeology (see the plan's Round 24 investigation, which needed exactly that to
     * root-cause a stale-build-timing false alarm). Reads {@code TestListener.class}'s own
     * classloader resource — works identically whether classes load from a directory (normal
     * dev/CI runs) or from inside a jar.
     */
    private static void logBuildFingerprint() {
        try {
            java.net.URL classUrl = TestListener.class.getResource(TestListener.class.getSimpleName() + ".class");
            long compiledAt = classUrl != null ? classUrl.openConnection().getLastModified() : 0;
            Log.info("[BuildFingerprint] Classes on this JVM's classpath were compiled at: {} — if this "
                    + "looks older than your last source edit, this run loaded STALE classes; rebuild and re-run.",
                    compiledAt > 0 ? java.time.Instant.ofEpochMilli(compiledAt) : "unknown");
        } catch (Exception e) {
            Log.warn("[BuildFingerprint] Could not resolve build timestamp: {}", e.getMessage());
        }
    }

    /**
     * Fires once per {@code <suite>}, after every {@code <test>} block has finished. Drains and logs
     * out every session in {@link com.gsl.driver.SessionPool} (moved here from the per-{@code <test>}
     * hook below in Round 22 — {@code IDLE}/{@code BORROWED} are single, JVM-wide, shared-across-blocks
     * fields, so draining at ANY one block's own finish while a sibling block is still running under
     * {@code parallel="tests"} would destroy sessions genuinely still in use elsewhere, and did — see
     * the plan's Round 22 for the live evidence, traced from an {@code UnreachableBrowserException}
     * back to this drain destroying a still-borrowed session out from under another thread. Only safe
     * once every block has truly finished, which is exactly this hook's own contract), then builds the
     * summary report, flushes the Extent report (which also republishes {@code reports/latest/}), and
     * archives this run's logs via {@link LogArchiver}.
     */
    @Override
    public void onFinish(ISuite suite) {
        com.gsl.driver.SessionPool.drainAll("end of suite: " + suite.getName());
        SummaryReportBuilder.build(suite);
        // Document-only per-run workbook (planned catalog vs. executed results). Failure-safe by
        // contract — buildSafely never throws, so it can't affect the suite's outcome.
        try {
            java.nio.file.Path reportPath = java.nio.file.Paths.get(com.gsl.constants.FrameworkConstants.getReportPath());
            com.gsl.reports.RunMeta runMeta = com.gsl.reports.RunMeta.finish(
                    reportPath.getParent().getFileName().toString());
            com.gsl.reports.CoverageWorkbookBuilder.buildSafely(reportPath.getParent(),
                    reportPath.getFileName().toString(), runMeta, coverageFilters);
            com.gsl.reports.RunEnvironmentFile.writeSafely(reportPath.getParent(), runMeta);
        } catch (Throwable t) {
            Log.warn("[CoverageWorkbook] Skipped: {}", t.toString());
        }
        // flushReports() also republishes reports/latest/index.html (see ExtentReport) —
        // no separate copyToLatest() call needed here anymore.
        try {
            // Browser builds are only known once sessions exist, so they join the Extent header here, just before the final flush.
            var builds = com.gsl.reports.BrowserFingerprint.browserVersions();
            if (!builds.isEmpty() && ExtentReport.getExtentReports() != null) {
                ExtentReport.getExtentReports().setSystemInfo("Browser Build", String.join("; ", builds));
            }
        } catch (Throwable t) {
            Log.warn("[BrowserVersions] Extent header not updated: {}", t.toString());
        }
        ExtentReport.flushReports();
        // Bundles this run's logs into reports/runs/<runId>/logs.zip, right next to the report
        // flushed above — see LogArchiver's own Javadoc for why this depends on log4j2.xml's
        // OnStartupTriggeringPolicy to be a genuinely per-run archive.
        LogArchiver.archiveRunLogs();
        Log.logHeap("suite-finish:" + suite.getName());
    }

    // ── Test-context lifecycle — fires once per <test> element ───────────────────

    /** Fires once per {@code <test>} element, before its methods start. Logs the context name only. */
    @Override
    public void onStart(ITestContext context) {
        Log.info("SUITE STARTED: {}", context.getName());
        ReportLogger.framework("TEST CONTEXT STARTED: {}", context.getName());
    }

    /**
     * Fires once per {@code <test>} element, after all its methods have run. Clears
     * {@link com.gsl.driver.ExecutionContext} for the calling thread — safe per-block since it's
     * {@code ThreadLocal}-scoped and only ever clears the calling thread's own state. Session pool
     * draining does NOT happen here (moved to {@link #onFinish(ISuite)} in Round 22 — see that
     * method's Javadoc for why draining per-block is unsafe under {@code parallel="tests"}).
     */
    @Override
    public void onFinish(ITestContext context) {
        Log.info("SUITE FINISHED: {}", context.getName());
        ReportLogger.framework("TEST CONTEXT FINISHED: {}", context.getName());

        com.gsl.driver.ExecutionContext.clear();
    }

    // ── Per-test callbacks ────────────────────────────────────────────────────────

    /**
     * Fires before each {@code @Test} method invocation (including each retry and each
     * data-provider row). Skips immediately if {@link SuiteCircuitBreaker#isTripped()}; otherwise
     * resets the per-test buffers ({@link com.gsl.reports.ActionTrailBuffer}, {@link StepDurationBuffer},
     * {@link RetryHistoryBuffer}, {@link PopupTrailBuffer}, …), creates the ExtentReport test node
     * with a rich title/description built from {@link TestCaseId}, {@link FrameworkAnnotation}, and
     * the resolved {@link DomainReporter} (or renders a lightweight "Rule Validation" node for
     * {@link FrameworkValidation} methods), logs the correlation block (execution id/user/session),
     * and injects the browser JS/network log capture for this test.
     */
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
        TestContextManager.setBrowser(com.gsl.config.BrowserResolver.name());
        TestContextManager.setEnv(ConfigFactory.getConfig().env());
        com.gsl.reports.ActionTrailBuffer.clear();
        StepDurationBuffer.clear();
        com.gsl.validation.ui.UiFailureCollector.clear();   // deferred UI failures never leak into the next test
        com.gsl.validation.ui.UiValidationReporter.clearHeld();
        com.gsl.validation.ui.UiStyleScope.clear();         // nor does the previous test's voucher style scope
        com.gsl.utils.Selenium.PageStateDiagnostics.reset();
        ScreenshotPolicy.discard();                         // nor do unattached screenshots
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
                com.gsl.config.BrowserResolver.name(),
                ConfigFactory.getConfig().env(),
                Thread.currentThread().getName(),
                username,
                sessionLabel != null ? sessionLabel : "n/a",
                previousTestCase != null ? previousTestCase : "n/a"));

        renderUserPoolStatusTable();

        // Inject JS logger and reset log buffers so this test sees only its own activity
        if (ConfigFactory.getNetworkConfig().browserLogsEnabled() && DriverManager.getDriver() != null) {
            AdvancedJsLogger.inject();
            AdvancedJsLogger.clearLogs();
        }

        // Reset network log buffer so this test only sees its own traffic
        NetworkCaptureManager.get().clear();
    }

    /**
     * Renders the Extent report's "User Pool Status" table — User / Test Case ID / Status — at
     * the start of every real test. Must run AFTER {@code SessionPool.updateCurrentLabel} (called
     * a few lines above, in {@link #onTestStart}) so a REUSED session's row shows the test case
     * that's using it right now, not the one that first created the session (see {@code
     * SessionPool.currentTestCaseIdsByUsername()}'s Javadoc for why that distinction matters).
     * Joins {@link UserPool#allUsernamesOfType} (the full configured roster) with that live
     * borrower map: a username present there is {@code Used} (+ its current test case), every
     * other configured username is {@code Unused}. Best-effort — a reporting problem here must
     * never fail the test itself.
     */
    private void renderUserPoolStatusTable() {
        try {
            java.util.Map<String, String> liveTestCaseIdsByUsername =
                    com.gsl.driver.SessionPool.currentTestCaseIdsByUsername();

            List<UserPoolStatusRow> rows = new java.util.ArrayList<>();
            for (UserType type : UserType.values()) {
                for (String username : UserPool.allUsernamesOfType(type)) {
                    String liveTestCaseId = liveTestCaseIdsByUsername.get(username);
                    rows.add(liveTestCaseId != null
                            ? new UserPoolStatusRow(username, UserPoolStatusRow.USED, liveTestCaseId)
                            : new UserPoolStatusRow(username, UserPoolStatusRow.UNUSED, ""));
                }
            }
            if (rows.isEmpty() || !ExtentManager.hasTest()) {
                return;
            }

            String[][] table = new String[rows.size() + 1][3];
            table[0] = new String[]{"User", "Test Case ID", "Status"};
            for (int i = 0; i < rows.size(); i++) {
                UserPoolStatusRow row = rows.get(i);
                table[i + 1] = new String[]{row.username(), row.testCaseId(), row.status()};
            }

            ExtentLogger.infoHtmlOnChildNode("User Pool Status", FoldableSectionRenderer.foldableTable(
                    FoldableSectionRenderer.ICO_SUBLEDGER, "User Pool Status", false, table));
        } catch (Exception e) {
            Log.warn("[TestListener] renderUserPoolStatusTable failed: {}", e.getMessage());
        }
    }

    /**
     * Fires when a {@code @Test} method passes. Records the pass to {@link SuiteCircuitBreaker},
     * flushes the domain reporter's "Actual Results" and the diagnostic bundle, forces the Extent
     * node's status to {@code PASS} (so a recovered fluent-wait retry-throw logged by
     * {@link com.gsl.aspects.LoggingAspect} doesn't paint a passing test red), and clears the
     * per-test context. Also handles the lightweight {@link FrameworkValidation} "Rule Validation"
     * path via {@code frameworkCheckLabel} — see the field's own comment.
     */
    @Override
    public void onTestSuccess(ITestResult result) {
        com.gsl.reports.ResultCollector.record(result, "PASS");
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
        // A passing test has nothing to show: screenshots captured for internally-recovered
        // failures are dropped rather than attached.
        ScreenshotPolicy.discard();
        Log.info("TEST PASSED: {}", result.getName());
        ReportLogger.framework("TEST PASSED: {}", result.getName());
        // Trailing child node (not a flat top-level entry) — the bundled ExtentReports Spark
        // template renders all flat top-level logs before every step node regardless of real
        // timestamp, so a flat "is passed" marker here used to render ABOVE every step node
        // (e.g. "Login to Application") even though it's actually the LAST thing that happened.
        ExtentLogger.passOnChildNode("Test Result", result.getName() + " is passed");

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

    /**
     * Fires when a {@code @Test} method fails. Records the failure to {@link SuiteCircuitBreaker}
     * (which may trip the breaker and abort the suite), flushes the domain reporter's "Actual
     * Results", classifies the failure via {@link RootCauseClassifier} and renders it as a banner
     * through {@link FailureSummaryRenderer}, attaches the full diagnostic bundle via
     * {@link FailureBundleOrchestrator}, and clears the per-test context. Also handles the
     * lightweight {@link FrameworkValidation} "Rule Validation" failure path — see
     * {@code frameworkCheckLabel}'s own comment.
     */
    @Override
    public void onTestFailure(ITestResult result) {
        com.gsl.reports.ResultCollector.record(result, "FAIL");
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
        // Attach the failure screenshot(s) the configured report.screenshots.mode keeps (default
        // LAST = just the final one), delete the rest.
        try {
            ScreenshotPolicy.flush();
        } catch (Exception e) {
            Log.warn("[TestListener] Screenshot policy flush failed: {}", e.getMessage());
        }
        // Safety net: UI-contract failures that were deferred but never reported because the flow
        // failed first (e.g. an Edit snapshot failure) must still be visible in the report.
        com.gsl.validation.ui.UiValidationReporter.flushHeld();
        if (com.gsl.validation.ui.UiFailureCollector.hasPending()) {
            StringBuilder pendingHtml = new StringBuilder("<b>UI-contract failures found earlier "
                    + "(deferred; the flow then failed):</b><br>");
            for (com.gsl.validation.ui.UiValidationFailure f : com.gsl.validation.ui.UiFailureCollector.pending()) {
                pendingHtml.append("&#8226; ").append(f.shortLabel()
                        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")).append("<br>");
            }
            ExtentLogger.infoHtmlOnChildNode("Deferred UI failures", pendingHtml.toString());
            com.gsl.validation.ui.UiFailureCollector.clear();
        }
        Log.error("TEST FAILED: {}", result.getName());
        Log.error("Failure cause: {}",
                result.getThrowable() != null ? result.getThrowable().getMessage() : "no throwable");
        ReportLogger.frameworkError("TEST FAILED: {} — {}",
                result.getName(),
                result.getThrowable() != null ? result.getThrowable().getMessage() : "no throwable");
        // Trailing child node, not a flat top-level entry — same reordering fix as onTestSuccess's
        // "is passed" marker above; full exception details are in the StepExecutor child node.
        ExtentLogger.failOnChildNode("Test Result", result.getName() + " FAILED");

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
            // Own trailing node rather than a flat top-level entry — same reordering fix as above.
            ExtentLogger.infoHtmlOnChildNode("Runtime Snapshot", "<b>Runtime Snapshot:</b> " + snapshotPath);
        }

        ExtentReport.recordTestResult(result, "FAIL");
        TestContextManager.clear();
        ExtentManager.clearAll();
    }

    /**
     * Fires when a {@code @Test} method is skipped — either a genuine {@code SkipException} (e.g.
     * data-provider-driven data-unavailability skip, or {@link SuiteCircuitBreaker} having tripped)
     * or a data-provider failure surfacing as a skip. When {@link #onTestStart(ITestResult)} never
     * created a node (circuit breaker tripped before it ran, or the failure happened at
     * DataProvider time), builds one here from the method's annotations; otherwise enriches an
     * already-created node's title with the now-bound row parameters (handles the parallel
     * {@code @DataSupplier} race where parameters aren't bound yet at {@code onTestStart} time).
     */
    @Override
    public void onTestSkipped(ITestResult result) {
        com.gsl.reports.ResultCollector.record(result, "SKIP");
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
            // Trailing child node, not a flat top-level entry — same reordering fix as
            // onTestSuccess/onTestFailure's markers above (this branch only runs when a node
            // already exists, i.e. some steps already ran before the skip).
            ExtentLogger.skipOnChildNode("Test Result", "Test skipped" + skipReason);
        }
        ExtentReport.recordTestResult(result, "SKIP");
        TestContextManager.clear();
        ExtentManager.clearAll();
    }

}
