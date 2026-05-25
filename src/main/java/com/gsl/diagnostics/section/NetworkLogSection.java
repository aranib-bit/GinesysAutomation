package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.network.HarFileGenerator;
import com.gsl.network.NetworkLogRecord;
import com.gsl.network.pattern.ApiPattern;
import com.gsl.reports.NetworkExtentLogger;
import com.gsl.utils.TestContextManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class NetworkLogSection implements DiagnosticSection {

    private final List<NetworkLogRecord> logs;
    private final String                 protocol;
    private final String                 label;
    private final Set<String>            correlatedUrls;

    /** Shows all captured traffic for this session (no correlation highlighting). */
    public NetworkLogSection(List<NetworkLogRecord> logs, String protocol) {
        this(logs, protocol, (ApiPattern) null);
    }

    /**
     * Shows all captured traffic with correlation highlighting.
     * Rows whose URL appears in correlatedUrls get an amber "⚠ Near JS Error" badge.
     */
    public NetworkLogSection(List<NetworkLogRecord> logs, String protocol, Set<String> correlatedUrls) {
        this.protocol       = protocol;
        this.logs           = logs != null ? logs : List.of();
        this.label          = null;
        this.correlatedUrls = correlatedUrls != null ? correlatedUrls : Set.of();
    }

    /**
     * Shows only traffic matching the given ApiPattern.
     * The label in the section title becomes the pattern's displayName.
     */
    public NetworkLogSection(List<NetworkLogRecord> allLogs, String protocol, ApiPattern filter) {
        this.protocol       = protocol;
        this.correlatedUrls = Set.of();
        if (filter != null) {
            this.logs  = filter.filterLogs(allLogs);
            this.label = filter.displayName();
        } else {
            this.logs  = allLogs != null ? allLogs : List.of();
            this.label = null;
        }
    }

    @Override
    public String sectionTitle() {
        String base = "Network Traffic [" + protocol + "] — " + logs.size() + " requests";
        return label != null ? base + "  (" + label + ")" : base;
    }

    @Override
    public String renderHtml() {
        if (logs.isEmpty()) {
            String msg = label != null
                    ? "No <b>" + label + "</b> requests were captured for this test. "
                    + "Possible causes: the save action was not triggered, the URL pattern in "
                    + "<code>FinanceApiPattern." + label.replace(" ", "_").toUpperCase() + "</code> "
                    + "does not match the actual endpoint, or <code>network.capture.url.filter</code> "
                    + "in config.properties is blocking the request."
                    : "No network requests were captured. "
                    + "Check <code>framework.log</code> for "
                    + "<code>[CdpNetworkCapture] Failed to start</code> warnings, "
                    + "and verify <code>enable.network.logs=true</code> is set in config.properties.";
            return "<div style='color:#888;font-style:italic;padding:4px 0;'>" + msg + "</div>";
        }
        return NetworkExtentLogger.renderTable(logs, correlatedUrls);
    }

    /** Generates a HAR file for the captured traffic and returns an HTML download link. */
    private String buildHarLink() {
        try {
            String testName = TestContextManager.getTestCaseId();
            Path harPath = HarFileGenerator.generate(logs, testName != null ? testName : "network");
            if (harPath == null) return "";
            // Relative path from the reports/index.html to the HAR file
            String relativePath = "har/" + harPath.getFileName().toString();
            return "<div style='margin-top:6px;font-size:11px;'>"
                 + "<a href='" + relativePath + "' download style='color:#0066cc;text-decoration:none;'>"
                 + "&#x2B07; Download HAR file</a></div>";
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public boolean hasData() {
        // "NONE" = capture disabled (NoOpNetworkCapture). Any real protocol (CDP/BiDi)
        // means capture was attempted — always show the section so empty results
        // (which indicate a CDP session failure) are visible rather than silently hidden.
        return !"NONE".equals(protocol);
    }
}
