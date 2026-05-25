package com.gsl.reports;

import com.gsl.config.AppLogPathsConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.config.DataBaseConfig;
import com.gsl.config.ExcelDataConfig;
import com.gsl.config.FrameworkConfig;
import com.gsl.reports.profile.BrowserConfig;
import com.gsl.reports.profile.DatabaseConfig;
import com.gsl.reports.profile.DiagnosticsConfig;
import com.gsl.reports.profile.ExecutionConfig;
import com.gsl.reports.profile.NetworkConfig;
import com.gsl.reports.profile.PerformanceConfig;
import com.gsl.reports.profile.RemoteLogConfig;
import com.gsl.reports.profile.RemoteServerConfig;
import com.gsl.reports.profile.RuntimeExecutionProfile;
import com.gsl.reports.profile.TestDataConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeProfileBuilder {

    private static final String MASKED = "********";

    private RuntimeProfileBuilder() {
    }

    public static RuntimeExecutionProfile build() {
        FrameworkConfig fw = ConfigFactory.getConfig();
        DataBaseConfig db = ConfigFactory.getDataBaseConfig();
        ExcelDataConfig ex = ConfigFactory.getExcelDataConfig();
        AppLogPathsConfig logPaths = ConfigFactory.getAppLogPathsConfig();

        return RuntimeExecutionProfile.builder()
                .execution(buildExecution(fw))
                .browser(buildBrowser(fw))
                .database(buildDatabase(db))
                .network(buildNetwork(fw))
                .diagnostics(buildDiagnostics(fw))
                .remoteLogs(buildRemoteLogs(fw))
                .remoteServer(buildRemoteServer(fw))
                .testData(buildTestData(fw, ex))
                .performance(buildPerformance(fw))
                .appLogSources(buildLogSources(logPaths))
                .build();
    }

    private static ExecutionConfig buildExecution(FrameworkConfig fw) {
        return ExecutionConfig.builder()
                .environment(fw.env())
                .browser(fw.browser())
                .runMode(fw.runmode())
                .executionType(fw.executionType())
                .threadCount(fw.threadCount())
                .concurrentUserCount(fw.concurrentUserCount())
                .applicationType(fw.applicationType())
                .applicationUrl(fw.url())
                .gridUrl(fw.gridUrl())
                .dockerGridUrl(fw.dockerGridUrl())
                .remoteAppServerHost(fw.remoteAppServerHost())
                .build();
    }

    private static BrowserConfig buildBrowser(FrameworkConfig fw) {
        return BrowserConfig.builder()
                .headless(fw.headless())
                .incognito(fw.incognito())
                .acceptInsecureCerts(fw.acceptInsecureCerts())
                .itemScanTimeout(fw.itemscanmaxtimeout())
                .extjsTimeout(fw.extjsTimeout())
                .extjsMinTimeout(fw.extjsMinTimeout())
                .clickOverlayRetryMax(fw.clickOverlayRetryMax())
                .syncTimeoutSeconds(fw.syncTimeoutSeconds())
                .docSelectorMode(fw.docSelectorMode())
                .docSelectorOverfetchMultiplier(fw.docSelectorOverfetchMultiplier())
                .docSelectorRetryBackoffMs(fw.docSelectorRetryBackoffMs())
                .docSelectorDocumentCountDefault(fw.docSelectorDocumentCountDefault())
                .build();
    }

    private static DatabaseConfig buildDatabase(DataBaseConfig db) {
        return DatabaseConfig.builder()
                .type(db.databaseType())
                .name(db.databasename())
                .host(db.databaseHost())
                .port(db.databasePort())
                .username(MASKED)
                .password(MASKED)
                .poolName(db.PoolName())
                .maximumPoolSize(db.MaximumPoolSize())
                .minimumIdle(db.MinimumIdle())
                .idleTimeout(db.IdleTimeout())
                .connectionTimeout(db.ConnectionTimeout())
                .maxLifetime(db.MaxLifetime())
                .leakDetectionThreshold(db.LeakDetectionThreashold())
                .build();
    }

    private static NetworkConfig buildNetwork(FrameworkConfig fw) {
        return NetworkConfig.builder()
                .networkMode(fw.networkMode())
                .enabled(fw.enableNetworkLogs())
                .captureFailedOnly(fw.networkCaptureFailedOnly())
                .captureHeaders(fw.networkCaptureHeaders())
                .captureBody(fw.networkCaptureBody())
                .urlFilter(fw.networkCaptureUrlFilter().isEmpty() ? "ALL" : fw.networkCaptureUrlFilter())
                .passShowAll(fw.networkCapturePassShowAll())
                .timingLogOn(fw.networkTimingLogOn())
                .cookiesLogOn(fw.networkCookiesLogOn())
                .callstackLogOn(fw.networkCallstackLogOn())
                .browserLogsEnabled(fw.browserLogsEnabled())
                .browserLogsCaptureOnFailure(fw.browserLogsCaptureOnFailure())
                .browserLogsFailOnJsError(fw.browserLogsFailOnJsError())
                .browserLogsFailOnCriticalJsError(fw.browserLogsFailOnCriticalJsError())
                .browserLogsCaptureOnly(fw.browserLogsCaptureOnly())
                .browserLogsLevel(fw.browserLogsLevel())
                .build();
    }

    private static DiagnosticsConfig buildDiagnostics(FrameworkConfig fw) {
        return DiagnosticsConfig.builder()
                .htmlCaptureOnElementFailure(fw.diagnosticsHtmlCaptureOnElementFailure())
                .driverCapabilitiesEnabled(fw.diagnosticsDriverCapabilitiesEnabled())
                .activePopupEnabled(fw.diagnosticsActivePopupEnabled())
                .dbStateEnabled(fw.diagnosticsDbStateEnabled())
                .threadDumpEnabled(fw.diagnosticsThreadDumpEnabled())
                .jsErrorJsonExportEnabled(fw.jsErrorJsonExportEnabled())
                .uiDiagnosticsEnabled(fw.uiDiagnosticsEnabled())
                .appServerUtilEnabled(fw.diagnosticsAppServerUtilEnabled())
                .appServerHost(fw.diagnosticsAppServerHost())
                .appServerType(fw.diagnosticsAppServerType())
                .dbServerUtilEnabled(fw.diagnosticsDbServerUtilEnabled())
                .dbServerHost(fw.diagnosticsDbServerHost())
                .dbServerType(fw.diagnosticsDbServerType())
                .eventLogEnabled(fw.diagnosticsEventLogEnabled())
                .eventLogLines(fw.diagnosticsEventLogLines())
                .build();
    }

    private static RemoteLogConfig buildRemoteLogs(FrameworkConfig fw) {
        return RemoteLogConfig.builder()
                .captureMode(fw.remoteLogsCaptureMode())
                .maxLines(fw.remoteLogsMaxLines())
                .debugEnabled(fw.remoteLogsDebugEnabled())
                .sshRetryCount(fw.remoteSshRetryCount())
                .sshCommandTimeoutSeconds(fw.remoteSshCommandTimeoutSeconds())
                .winrmOperationTimeoutSeconds(fw.remoteLogsWinrmOperationTimeoutSeconds())
                .serverTimezoneOffset(fw.remoteLogsServerTimezoneOffset())
                .keywords(fw.remoteLogsKeywords())
                .build();
    }

    private static RemoteServerConfig buildRemoteServer(FrameworkConfig fw) {
        return RemoteServerConfig.builder()
                .appHost(fw.remoteAppServerHost())
                .appSshPort(fw.remoteAppServerSshPort())
                .appSshUsername(MASKED)
                .appSshPassword(MASKED)
                .appWinrmPort(fw.remoteAppServerWinrmPort())
                .appWinrmUseHttps(fw.remoteAppServerWinrmUseHttps())
                .appWinrmUsername(MASKED)
                .appWinrmPassword(MASKED)
                .dbHost(fw.remoteDbServerHost())
                .dbSshPort(fw.remoteDbServerSshPort())
                .dbSshUsername(MASKED)
                .dbSshPassword(MASKED)
                .dbWinrmPort(fw.remoteDbServerWinrmPort())
                .dbWinrmUseHttps(fw.remoteDbServerWinrmUseHttps())
                .dbWinrmUsername(MASKED)
                .dbWinrmPassword(MASKED)
                .build();
    }

    private static TestDataConfig buildTestData(FrameworkConfig fw, ExcelDataConfig ex) {
        return TestDataConfig.builder()
                .source(fw.testDataSource())
                .masterTestData(ex.MasterTestData())
                .receiptsData(ex.receiptsPath())
                .inventoryGroupData(ex.inventorygroupPath())
                .inventoryItemData(ex.inventoryitemPath())
                .arVoucherData(ex.arVoucherPath())
                .usersListData(ex.usersListPath())
                .build();
    }

    private static PerformanceConfig buildPerformance(FrameworkConfig fw) {
        return PerformanceConfig.builder()
                .timingEnabled(fw.performanceTimingEnabled())
                .actionTrailBufferSize(fw.actionTrailBufferSize())
                .actionTrailDisplayLimit(fw.actionTrailDisplayLimit())
                .customMetricsEnabled(fw.customMetricsEnabled())
                .customMetricsCaptureMode(fw.customMetricsCaptureMode())
                .slowResourceThresholdMs(fw.customMetricsSlowResourceThresholdMs())
                .userTimingWarnMs(fw.customMetricsUserTimingWarnMs())
                .failOnLongTaskCount(fw.customMetricsFailOnLongTaskCount())
                .build();
    }

    private static Map<String, String> buildLogSources(AppLogPathsConfig logPaths) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Finance API Error",      configured(logPaths.financeApiError()));
        sources.put("Finance API Info",       configured(logPaths.financeApiInfo()));
        sources.put("Finance API EF Logger",  configured(logPaths.financeApiEfLogger()));
        sources.put("WebAPI Error",           configured(logPaths.webapiError()));
        sources.put("WebAPI Info",            configured(logPaths.webapiInfo()));
        sources.put("WebAPI Debug",           configured(logPaths.webapiDebug()));
        sources.put("NHibernate SQL",         configured(logPaths.webapiNhibernate()));
        sources.put("WebUI Trace",            configured(logPaths.webuiTrace()));
        return sources;
    }

    private static String configured(String path) {
        return (path == null || path.isBlank()) ? "Not Configured" : "Configured";
    }
}
