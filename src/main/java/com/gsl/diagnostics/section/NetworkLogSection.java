package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.network.HarFileGenerator;
import com.gsl.network.NetworkLogRecord;
import com.gsl.network.pattern.ApiPattern;
import com.gsl.reports.NetworkExtentLogger;
import com.gsl.utils.TestContextManager;

import java.nio.file.Path;
import java.util.List;

public final class NetworkLogSection implements DiagnosticSection {

    private final List<NetworkLogRecord> logs;
    private final String                 protocol;
    private final String                 label;

    /** Shows all captured traffic for this session. */
    public NetworkLogSection(List<NetworkLogRecord> logs, String protocol) {
        this(logs, protocol, null);
    }

    /**
     * Shows only traffic matching the given ApiPattern.
     * The label in the section title becomes the pattern's displayName.
     */
    public NetworkLogSection(List<NetworkLogRecord> allLogs, String protocol, ApiPattern filter) {
        this.protocol = protocol;
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
        return NetworkExtentLogger.renderTable(logs);
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
        return !logs.isEmpty();
    }
}
