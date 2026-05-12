package com.gsl.config;

import com.gsl.config.converter.StringToBrowserConverter;
import com.gsl.config.converter.StringToRunModeConverter;
import com.gsl.enums.Browser;
import com.gsl.enums.RunMode;
import org.aeonbits.owner.Config;

@Config.Sources(value = "file:${user.dir}/src/test/resources/config/config.properties")
public interface FrameworkConfig extends Config {
    long timeout();

    String url();

    @DefaultValue("local")
    String env();

    /** Raw string — preserved for backward compat. Prefer browserType() for typed access. */
    String browser();

    /** Raw string — preserved for backward compat. Prefer runMode() for typed access. */
    String runmode();

    /** Typed Browser enum converted from the browser property via StringToBrowserConverter. */
    @Key("browser")
    @Config.ConverterClass(StringToBrowserConverter.class)
    Browser browserType();

    /** Typed RunMode enum converted from the runmode property via StringToRunModeConverter. */
    @Key("runmode")
    @Config.ConverterClass(StringToRunModeConverter.class)
    RunMode runMode();

    String remoteurl();

    long itemscanmaxtimeout();

    /** EXCEL | DB — selects which DataSource implementation loads AR Voucher test data. */
    @Key("testdata.source")
    @DefaultValue("EXCEL")
    String testDataSource();

    /** App login username used in DB mode (must match hrdemp.abbre in the database). */
    @Key("appusername")
    String username();

    /** App login password used in DB mode. */
    @Key("apppassword")
    String password();

    /** Tolerance in seconds for finvchmain.creation_dt vs UI save time. */
    @Key("db.creation.tolerance")
    @DefaultValue("60")
    long dbCreationTolerance();

    /** Max wait for ExtJS synchronization (seconds). Reduce for fast networks. */
    @Key("extjs.timeout")
    @DefaultValue("40")
    long extjsTimeout();

    /** Floor for ExtJS timeout — effective timeout is always max(extjs.min.timeout, extjs.timeout). */
    @Key("extjs.min.timeout")
    @DefaultValue("10")
    long extjsMinTimeout();

    /** Max retries for the non-critical warning-message text read in verifyWarningMessage().
     *  Each attempt uses a 3-second timeout; default is 2. Configurable in config.properties. */
    @Key("warning.msg.max.retries")
    @DefaultValue("2")
    int warningMsgMaxRetries();

    /** Buffer sleep (ms) after resetGridScrollToTop() before the loader wait. */
    @Key("selectdoc.grid.load.buffer.ms")
    @DefaultValue("200")
    int selectDocGridLoadBufferMs();

    /** Render wait (ms) after jumpGridToDocument() scrolls to the target row. */
    @Key("selectdoc.jump.render.ms")
    @DefaultValue("500")
    int selectDocJumpRenderMs();

    /** Max proportional scroll attempts in the scroll-poll loop. */
    @Key("selectdoc.scroll.max.attempts")
    @DefaultValue("20")
    int selectDocScrollMaxAttempts();

    /** Minimum allowed scroll attempts — effective attempts = max(min, configured). */
    @Key("selectdoc.scroll.min.attempts")
    @DefaultValue("5")
    int selectDocScrollMinAttempts();

    /** Sleep (ms) between each proportional scroll attempt. */
    @Key("selectdoc.scroll.step.wait.ms")
    @DefaultValue("400")
    int selectDocScrollStepWaitMs();

    /** Render wait (ms) after focusRow() — needs to be longer for large (7000+) grids. */
    @Key("selectdoc.focusrow.render.ms")
    @DefaultValue("1500")
    int selectDocFocusRowRenderMs();

    /** Buffer sleep (ms) before the checkbox click after the row becomes visible. */
    @Key("selectdoc.click.buffer.ms")
    @DefaultValue("300")
    int selectDocClickBufferMs();

    /** Max retry attempts for the loader-disappear wait in AdjustmentEntry.selectDocument().
     *  Each attempt uses extjsTimeout() seconds; default 3 gives up to 3x40=120s total. */
    @Key("selectdoc.loader.max.retries")
    @DefaultValue("3")
    int selectDocLoaderMaxRetries();

    /**
     * Maximum number of click retries when an {@code ElementClickInterceptedException} is
     * thrown — typically because the transient {@code ext-element-*} modal overlay is still
     * visible.  Each retry waits for all ExtJS masks and ext-element overlays to clear before
     * re-attempting the click.  Default: 3.
     */
    @Key("click.overlay.retry.max")
    @DefaultValue("3")
    int clickOverlayRetryMax();

    /** Cheque generation strategy: INDIA | USA_3 | USA_4 | USA_5 | USA_6 | ALPHANUMERIC | ROTATE */
    @Key("cheque.country")
    @DefaultValue("ROTATE")
    String chequeCountry();

    // ─── Execution control ────────────────────────────────────────────────────

    /** SET1 (independent parallel) or SET2 (synchronized concurrent). */
    @Key("execution.type")
    @DefaultValue("SET1")
    String executionType();

    @Key("thread.count")
    @DefaultValue("5")
    int threadCount();

    @Key("concurrent.user.count")
    @DefaultValue("2")
    int concurrentUserCount();

    @Key("sync.timeout.seconds")
    @DefaultValue("30")
    long syncTimeoutSeconds();

    // ─── Browser behaviour ────────────────────────────────────────────────────

    @Key("headless")
    @DefaultValue("false")
    boolean headless();

    @Key("incognito")
    @DefaultValue("false")
    boolean incognito();

    @Key("acceptInsecureCerts")
    @DefaultValue("false")
    boolean acceptInsecureCerts();

    // ─── Remote execution providers ───────────────────────────────────────────

    @Key("grid.url")
    @DefaultValue("http://localhost:4444/wd/hub")
    String gridUrl();

    @Key("docker.grid.url")
    @DefaultValue("http://localhost:4444/wd/hub")
    String dockerGridUrl();

    @Key("browserstack.username")
    @DefaultValue("")
    String browserStackUsername();

    @Key("browserstack.accesskey")
    @DefaultValue("")
    String browserStackAccessKey();

    @Key("browserstack.build.name")
    @DefaultValue("CI Build")
    String browserStackBuildName();

    @Key("lambdatest.username")
    @DefaultValue("")
    String lambdaTestUsername();

    @Key("lambdatest.accesskey")
    @DefaultValue("")
    String lambdaTestAccessKey();

    // ─── Network / CDP ────────────────────────────────────────────────────────

    @Key("network.mode")
    @DefaultValue("CDP")
    String networkMode();

    @Key("enable.network.logs")
    @DefaultValue("false")
    boolean enableNetworkLogs();

    /** Capture only 4xx/5xx responses. false = capture all traffic. */
    @Key("network.capture.failed.only")
    @DefaultValue("false")
    boolean networkCaptureFailedOnly();

    /** Include request and response headers in the captured record. */
    @Key("network.capture.headers")
    @DefaultValue("true")
    boolean networkCaptureHeaders();

    /**
     * Capture request body (POST/PUT payloads) and response body.
     * CDP: both captured. BiDi: body unavailable without intercept — fields remain null.
     */
    @Key("network.capture.body")
    @DefaultValue("false")
    boolean networkCaptureBody();

    /**
     * Comma-separated URL substrings to capture (e.g. /api/,/finance/).
     * Empty = capture all URLs.
     */
    @Key("network.capture.url.filter")
    @DefaultValue("")
    String networkCaptureUrlFilter();

    // ─── Browser log capture ──────────────────────────────────────────────────

    @Key("browser.logs.enabled")
    @DefaultValue("false")
    boolean browserLogsEnabled();

    @Key("browser.logs.capture.on.failure")
    @DefaultValue("true")
    boolean browserLogsCaptureOnFailure();

    @Key("browser.logs.fail.on.js.error")
    @DefaultValue("false")
    boolean browserLogsFailOnJsError();

    @Key("browser.logs.fail.on.critical.js.error")
    @DefaultValue("true")
    boolean browserLogsFailOnCriticalJsError();

    @Key("browser.logs.capture.only")
    @DefaultValue("false")
    boolean browserLogsCaptureOnly();

    /** Comma-separated additional warning patterns (e.g. favicon,deprecated). */
    @Key("browser.logs.warning.patterns")
    @DefaultValue("")
    String browserLogsWarningPatterns();

    /** Comma-separated additional ignore patterns (e.g. chrome-extension,extensions::). */
    @Key("browser.logs.ignore.patterns")
    @DefaultValue("")
    String browserLogsIgnorePatterns();

    /** Minimum log level to attach on success: SEVERE | WARNING | INFO. */
    @Key("browser.logs.level")
    @DefaultValue("SEVERE")
    String browserLogsLevel();

    // ─── Unexpected popup detection ───────────────────────────────────────────

    /**
     * Maximum number of calendar days a back-dated AR Voucher document date may go into the past.
     * Variants whose {@code backDateDays} exceed this value are clamped to this ceiling before
     * the date is submitted; if the clamped date would still be invalid (e.g. 0 days back on a
     * closed month), the test self-skips rather than failing with a misleading UI error.
     * Default: 58 days (enough to cover the widest BACKDATE variant currently in the fixture catalogue).
     */
    @Key("finance.backdate.max.days")
    @DefaultValue("58")
    int financeBackdateMaxDays();

    /** Master switch for unexpected popup detection after business actions. */
    @Key("popup.detection.enabled")
    @DefaultValue("true")
    boolean popupDetectionEnabled();

    /** How long (ms) to wait for a messagebox to appear after a business action. */
    @Key("popup.detection.timeout.ms")
    @DefaultValue("1500")
    int popupDetectionTimeoutMs();

    // ─── Diagnostics — HTML capture ───────────────────────────────────────────

    /** Capture full page DOM to file on NoSuchElementException / TimeoutException failures. */
    @Key("diagnostics.html.capture.on.element.failure")
    @DefaultValue("true")
    boolean diagnosticsHtmlCaptureOnElementFailure();

    // ─── Diagnostics — App server utilization ────────────────────────────────

    /** Enable CPU + memory snapshot from the application server on test failure. */
    @Key("diagnostics.app.server.util.enabled")
    @DefaultValue("false")
    boolean diagnosticsAppServerUtilEnabled();

    /** OS type of the application server: WINDOWS or LINUX. */
    @Key("diagnostics.app.server.type")
    @DefaultValue("WINDOWS")
    String diagnosticsAppServerType();

    /** Hostname / IP of the application server. localhost = exec commands directly. */
    @Key("diagnostics.app.server.host")
    @DefaultValue("localhost")
    String diagnosticsAppServerHost();

    // ─── Diagnostics — DB server utilization ─────────────────────────────────

    /** Enable CPU + memory snapshot from the database server on test failure. */
    @Key("diagnostics.db.server.util.enabled")
    @DefaultValue("false")
    boolean diagnosticsDbServerUtilEnabled();

    /** OS type of the database server: WINDOWS or LINUX. */
    @Key("diagnostics.db.server.type")
    @DefaultValue("WINDOWS")
    String diagnosticsDbServerType();

    /** Hostname / IP of the database server. localhost = exec commands directly. */
    @Key("diagnostics.db.server.host")
    @DefaultValue("localhost")
    String diagnosticsDbServerHost();

    // ─── Diagnostics — Event log ──────────────────────────────────────────────

    /** Capture recent error-level entries from the system event log on test failure. */
    @Key("diagnostics.event.log.enabled")
    @DefaultValue("false")
    boolean diagnosticsEventLogEnabled();

    /** Number of recent error entries to capture from the event log. */
    @Key("diagnostics.event.log.lines")
    @DefaultValue("30")
    int diagnosticsEventLogLines();

    // ─── Diagnostics — Driver capabilities ───────────────────────────────────

    @Key("diagnostics.driver.capabilities.enabled")
    @DefaultValue("true")
    boolean diagnosticsDriverCapabilitiesEnabled();

    // ─── Diagnostics — Active popup capture ──────────────────────────────────

    @Key("diagnostics.active.popup.enabled")
    @DefaultValue("true")
    boolean diagnosticsActivePopupEnabled();

    /** CSS selector used to detect visible UI modals. Default is ExtJS window body. */
    @Key("diagnostics.popup.selector")
    @DefaultValue(".x-window-body")
    String diagnosticsPopupSelector();

    // ─── Diagnostics — DB state snapshot ────────────────────────────────────

    @Key("diagnostics.db.state.enabled")
    @DefaultValue("false")
    boolean diagnosticsDbStateEnabled();

    /** Read-only SELECT query for DB state capture on failure. */
    @Key("diagnostics.db.state.query")
    @DefaultValue("SELECT vchcode,vchdate,slcode,amount FROM finvchmain ORDER BY creation_dt DESC LIMIT 5")
    String diagnosticsDbStateQuery();

    // ─── Diagnostics — Thread dump ────────────────────────────────────────────

    @Key("diagnostics.thread.dump.enabled")
    @DefaultValue("false")
    boolean diagnosticsThreadDumpEnabled();

    // ─── Remote log collection ────────────────────────────────────────────────

    /**
     * Remote log capture trigger: ON_FAILURE | ALWAYS | NEVER.
     * NEVER disables remote log collection entirely (default — safe for local runs).
     */
    @Key("remote.logs.capture.mode")
    @DefaultValue("NEVER")
    String remoteLogsCaptureMode();

    @Key("remote.logs.ssh.port")
    @DefaultValue("22")
    int remoteSshPort();

    @Key("remote.logs.ssh.connect.timeout.seconds")
    @DefaultValue("10")
    int remoteSshConnectTimeoutSeconds();

    @Key("remote.logs.ssh.read.timeout.seconds")
    @DefaultValue("30")
    int remoteSshReadTimeoutSeconds();

    @Key("remote.logs.ssh.command.timeout.seconds")
    @DefaultValue("60")
    int remoteSshCommandTimeoutSeconds();

    @Key("remote.logs.ssh.retry.count")
    @DefaultValue("3")
    int remoteSshRetryCount();

    /** ACCEPT_NEW | STRICT | DISABLED */
    @Key("remote.logs.ssh.host.key.verify")
    @DefaultValue("ACCEPT_NEW")
    String remoteSshHostKeyVerify();

    @Key("remote.logs.max.lines")
    @DefaultValue("500")
    long remoteLogsMaxLines();

    /** Comma-separated absolute log paths on the application server. */
    @Key("remote.logs.app.log.paths")
    @DefaultValue("/var/log/ginesys/app.log")
    String remoteLogsAppLogPaths();

    /** Comma-separated absolute log paths on the database server. */
    @Key("remote.logs.db.log.paths")
    @DefaultValue("/var/log/postgresql/postgresql.log")
    String remoteLogsDbLogPaths();

    /** Comma-separated keywords for grep filtering. Empty = time-window only. */
    @Key("remote.logs.keywords")
    @DefaultValue("ERROR,WARN,Exception")
    String remoteLogsKeywords();

    /** When true: log every command, retry, and byte offset for debugging. */
    @Key("remote.logs.debug.enabled")
    @DefaultValue("false")
    boolean remoteLogsDebugEnabled();

    // ─── Remote Machine Setup — Application Server ────────────────────────────

    /** Hostname or IP of the app server for remote log collection. Blank = disabled. */
    @Key("remote.app.server.host")
    @DefaultValue("")
    String remoteAppServerHost();

    /** Comma-separated absolute log paths on the app server. */
    @Key("remote.app.server.log.paths")
    @DefaultValue("/var/log/ginesys/app.log")
    String remoteAppServerLogPaths();

    /** SSH port on the application server. */
    @Key("remote.app.server.ssh.port")
    @DefaultValue("22")
    int remoteAppServerSshPort();

    /** WinRM port on the application server. */
    @Key("remote.app.server.winrm.port")
    @DefaultValue("5985")
    int remoteAppServerWinrmPort();

    /** When true, WinRM connects over HTTPS on the application server. */
    @Key("remote.app.server.winrm.use.https")
    @DefaultValue("false")
    boolean remoteAppServerWinrmUseHttps();

    // ─── Remote Machine Setup — Database Server ───────────────────────────────

    /** Hostname or IP of the DB server for remote log collection. Blank = disabled. */
    @Key("remote.db.server.host")
    @DefaultValue("")
    String remoteDbServerHost();

    /** Comma-separated absolute log paths on the DB server. */
    @Key("remote.db.server.log.paths")
    @DefaultValue("/var/log/postgresql/postgresql.log")
    String remoteDbServerLogPaths();

    /** SSH port on the database server. */
    @Key("remote.db.server.ssh.port")
    @DefaultValue("22")
    int remoteDbServerSshPort();

    /** WinRM port on the database server. */
    @Key("remote.db.server.winrm.port")
    @DefaultValue("5985")
    int remoteDbServerWinrmPort();

    /** When true, WinRM connects over HTTPS on the database server. */
    @Key("remote.db.server.winrm.use.https")
    @DefaultValue("false")
    boolean remoteDbServerWinrmUseHttps();

    // ─── SSH Connection Pool ──────────────────────────────────────────────────

    /** Max pooled SSH connections per host. */
    @Key("remote.logs.ssh.pool.size")
    @DefaultValue("5")
    int remoteSshPoolSize();

    /** Max seconds to wait for a pool slot before failing. */
    @Key("remote.logs.ssh.pool.max.wait.seconds")
    @DefaultValue("10")
    int remoteSshPoolMaxWaitSeconds();

    // ─── WinRM Timeouts ───────────────────────────────────────────────────────

    @Key("remote.logs.winrm.connect.timeout.seconds")
    @DefaultValue("10")
    int remoteLogsWinrmConnectTimeoutSeconds();

    @Key("remote.logs.winrm.operation.timeout.seconds")
    @DefaultValue("60")
    int remoteLogsWinrmOperationTimeoutSeconds();

    /** Max number of rotated log files to read per path pattern (most recent first). */
    @Key("remote.logs.winrm.rotated.max.files")
    @DefaultValue("3")
    int remoteLogsWinrmRotatedMaxFiles();

    // ─── Document selector (atomic SL + doc selection) ────────────────────────

    /**
     * Selection mode applied after atomic reservation.
     * POSITIVE = strict positive (default); MIXED = any non-zero; HYBRID = positive, fall back to mixed per SL.
     */
    @Key("doc.selector.mode")
    @DefaultValue("POSITIVE")
    String docSelectorMode();

    /** Over-fetch SLs from DB to absorb parallel contention. Effective pool = subLedgerCount × this. */
    @Key("doc.selector.overfetch.multiplier")
    @DefaultValue("3")
    int docSelectorOverfetchMultiplier();

    /** Sleep (ms) between retry attempts. */
    @Key("doc.selector.retry.backoff.ms")
    @DefaultValue("1500")
    long docSelectorRetryBackoffMs();

    /** Floor for {@code documentCount} when {@code ArVoucherFixtureOptions.defaults()} is used. */
    @Key("doc.selector.documentcount.default")
    @DefaultValue("3")
    int docSelectorDocumentCountDefault();
}
