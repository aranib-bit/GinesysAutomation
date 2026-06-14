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
                .network(buildNetwork())
                .diagnostics(buildDiagnostics())
                .remoteLogs(buildRemoteLogs())
                .remoteServer(buildRemoteServer())
                .testData(buildTestData(fw, ex))
                .performance(buildPerformance())
                .appLogSources(buildLogSources(logPaths))
                .build();
    }

    private static ExecutionConfig buildExecution(FrameworkConfig fw) {
        com.gsl.config.BrowserConfig bc   = ConfigFactory.getBrowserConfig();
        com.gsl.config.ExecutionConfig ec = ConfigFactory.getExecutionConfig();
        com.gsl.config.RemoteLogConfig rc = ConfigFactory.getRemoteLogConfig();
        return ExecutionConfig.builder()
                .environment(fw.env())
                .browser(fw.browser())
                .runMode(bc.runmode())
                .executionType(ec.executionType())
                .threadCount(ec.threadCount())
                .concurrentUserCount(ec.concurrentUserCount())
                .applicationType(bc.applicationType())
                .applicationUrl(fw.url())
                .gridUrl(bc.gridUrl())
                .dockerGridUrl(bc.dockerGridUrl())
                .remoteAppServerHost(rc.remoteAppServerHost())
                .build();
    }

    private static BrowserConfig buildBrowser(FrameworkConfig fw) {
        com.gsl.config.BrowserConfig bc    = ConfigFactory.getBrowserConfig();
        com.gsl.config.FinanceConfig fc    = ConfigFactory.getFinanceConfig();
        com.gsl.config.ExecutionConfig ec  = ConfigFactory.getExecutionConfig();
        com.gsl.config.WaitConfig wc       = ConfigFactory.getWaitConfig();
        return BrowserConfig.builder()
                .headless(bc.headless())
                .incognito(bc.incognito())
                .acceptInsecureCerts(bc.acceptInsecureCerts())
                .itemScanTimeout(fw.itemscanmaxtimeout())
                .extjsTimeout(fc.extjsTimeout())
                .extjsMinTimeout(fc.extjsMinTimeout())
                .clickOverlayRetryMax(wc.clickOverlayRetryMax())
                .syncTimeoutSeconds(ec.syncTimeoutSeconds())
                .docSelectorMode(wc.docSelectorMode())
                .docSelectorOverfetchMultiplier(wc.docSelectorOverfetchMultiplier())
                .docSelectorRetryBackoffMs(wc.docSelectorRetryBackoffMs())
                .docSelectorDocumentCountDefault(wc.docSelectorDocumentCountDefault())
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

    private static NetworkConfig buildNetwork() {
        com.gsl.config.NetworkConfig nc = ConfigFactory.getNetworkConfig();
        com.gsl.config.BrowserConfig bc = ConfigFactory.getBrowserConfig();
        return NetworkConfig.builder()
                .networkMode(bc.networkMode())
                .enabled(nc.enableNetworkLogs())
                .captureFailedOnly(nc.networkCaptureFailedOnly())
                .captureHeaders(nc.networkCaptureHeaders())
                .captureBody(nc.networkCaptureBody())
                .urlFilter(nc.networkCaptureUrlFilter().isEmpty() ? "ALL" : nc.networkCaptureUrlFilter())
                .passShowAll(nc.networkCapturePassShowAll())
                .timingLogOn(nc.networkTimingLogOn())
                .cookiesLogOn(nc.networkCookiesLogOn())
                .callstackLogOn(nc.networkCallstackLogOn())
                .browserLogsEnabled(nc.browserLogsEnabled())
                .browserLogsCaptureOnFailure(nc.browserLogsCaptureOnFailure())
                .browserLogsFailOnJsError(nc.browserLogsFailOnJsError())
                .browserLogsFailOnCriticalJsError(nc.browserLogsFailOnCriticalJsError())
                .browserLogsCaptureOnly(nc.browserLogsCaptureOnly())
                .browserLogsLevel(nc.browserLogsLevel())
                .build();
    }

    private static DiagnosticsConfig buildDiagnostics() {
        com.gsl.config.DiagnosticsConfig dc = ConfigFactory.getDiagnosticsConfig();
        com.gsl.config.ReportConfig rpc     = ConfigFactory.getReportConfig();
        return DiagnosticsConfig.builder()
                .htmlCaptureOnElementFailure(dc.diagnosticsHtmlCaptureOnElementFailure())
                .driverCapabilitiesEnabled(dc.diagnosticsDriverCapabilitiesEnabled())
                .activePopupEnabled(dc.diagnosticsActivePopupEnabled())
                .dbStateEnabled(dc.diagnosticsDbStateEnabled())
                .threadDumpEnabled(dc.diagnosticsThreadDumpEnabled())
                .jsErrorJsonExportEnabled(rpc.jsErrorJsonExportEnabled())
                .uiDiagnosticsEnabled(dc.uiDiagnosticsEnabled())
                .appServerUtilEnabled(dc.diagnosticsAppServerUtilEnabled())
                .appServerHost(dc.diagnosticsAppServerHost())
                .appServerType(dc.diagnosticsAppServerType())
                .dbServerUtilEnabled(dc.diagnosticsDbServerUtilEnabled())
                .dbServerHost(dc.diagnosticsDbServerHost())
                .dbServerType(dc.diagnosticsDbServerType())
                .eventLogEnabled(dc.diagnosticsEventLogEnabled())
                .eventLogLines(dc.diagnosticsEventLogLines())
                .build();
    }

    private static RemoteLogConfig buildRemoteLogs() {
        com.gsl.config.RemoteLogConfig rc = ConfigFactory.getRemoteLogConfig();
        return RemoteLogConfig.builder()
                .captureMode(rc.remoteLogsCaptureMode())
                .maxLines(rc.remoteLogsMaxLines())
                .debugEnabled(rc.remoteLogsDebugEnabled())
                .sshRetryCount(rc.remoteSshRetryCount())
                .sshCommandTimeoutSeconds(rc.remoteSshCommandTimeoutSeconds())
                .winrmOperationTimeoutSeconds(rc.remoteLogsWinrmOperationTimeoutSeconds())
                .serverTimezoneOffset(rc.remoteLogsServerTimezoneOffset())
                .keywords(rc.remoteLogsKeywords())
                .build();
    }

    private static RemoteServerConfig buildRemoteServer() {
        com.gsl.config.RemoteLogConfig rc = ConfigFactory.getRemoteLogConfig();
        return RemoteServerConfig.builder()
                .appHost(rc.remoteAppServerHost())
                .appSshPort(rc.remoteAppServerSshPort())
                .appSshUsername(MASKED)
                .appSshPassword(MASKED)
                .appWinrmPort(rc.remoteAppServerWinrmPort())
                .appWinrmUseHttps(rc.remoteAppServerWinrmUseHttps())
                .appWinrmUsername(MASKED)
                .appWinrmPassword(MASKED)
                .dbHost(rc.remoteDbServerHost())
                .dbSshPort(rc.remoteDbServerSshPort())
                .dbSshUsername(MASKED)
                .dbSshPassword(MASKED)
                .dbWinrmPort(rc.remoteDbServerWinrmPort())
                .dbWinrmUseHttps(rc.remoteDbServerWinrmUseHttps())
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

    private static PerformanceConfig buildPerformance() {
        com.gsl.config.DiagnosticsConfig dc = ConfigFactory.getDiagnosticsConfig();
        com.gsl.config.ReportConfig rpc     = ConfigFactory.getReportConfig();
        return PerformanceConfig.builder()
                .timingEnabled(rpc.performanceTimingEnabled())
                .actionTrailBufferSize(dc.actionTrailBufferSize())
                .actionTrailDisplayLimit(dc.actionTrailDisplayLimit())
                .customMetricsEnabled(dc.customMetricsEnabled())
                .customMetricsCaptureMode(dc.customMetricsCaptureMode())
                .slowResourceThresholdMs(dc.customMetricsSlowResourceThresholdMs())
                .userTimingWarnMs(dc.customMetricsUserTimingWarnMs())
                .failOnLongTaskCount(dc.customMetricsFailOnLongTaskCount())
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
