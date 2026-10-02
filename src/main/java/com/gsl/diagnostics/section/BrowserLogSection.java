package com.gsl.diagnostics.section;

import com.gsl.browser.BrowserLogRecord;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.reports.AdvancedExtentLogger;

import java.util.List;

/**
 * DiagnosticSection that renders captured browser console log entries.
 * Only created when {@code browser.logs.enabled=true} and the driver is still live
 * (see {@code FailureBundleOrchestrator.browserLogSection}).
 */
public final class BrowserLogSection implements DiagnosticSection {

    private final List<BrowserLogRecord> logs;
    private final String                 description;

    public BrowserLogSection(List<BrowserLogRecord> logs, String description) {
        this.logs        = logs != null ? logs : List.of();
        this.description = description;
    }

    @Override
    public String sectionTitle() {
        return "Browser Logs (" + logs.size() + " entries)";
    }

    @Override
    public String renderHtml() {
        if (logs.isEmpty()) {
            return "<div style='color:#888;font-style:italic;padding:4px 0;'>"
                 + "No browser log entries were captured. The JS logger shim may have been "
                 + "lost due to a full-page navigation after injection. "
                 + "Check that <code>browser.logs.enabled=true</code> is set and the app "
                 + "generates console output.</div>";
        }
        return AdvancedExtentLogger.buildUI(logs, description);
    }

    @Override
    public boolean hasData() {
        // Section is only created when browser.logs.enabled=true and driver is live
        // (see FailureBundleOrchestrator.browserLogSection). Always return true so the
        // section appears even when window.testLogs is empty, making capture failures visible.
        return true;
    }
}
