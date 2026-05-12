package com.gsl.diagnostics.section;

import com.gsl.browser.BrowserLogRecord;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.reports.AdvancedExtentLogger;

import java.util.List;

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
        return AdvancedExtentLogger.buildUI(logs, description);
    }

    @Override
    public boolean hasData() {
        return !logs.isEmpty();
    }
}
