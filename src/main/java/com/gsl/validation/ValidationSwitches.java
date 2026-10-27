package com.gsl.validation;

import com.gsl.config.ConfigFactory;
import com.gsl.config.FrameworkConfig;
import com.gsl.config.ReportConfig;
import com.gsl.database.DbAvailability;

/**
 * Two flag layers per validation area (UI / API / DB):
 * <ul>
 *   <li>{@code validation.<area>.enabled} — the validation <em>runs</em>; no skips it entirely (saves time, accepts the risk);</li>
 *   <li>{@code report.validation.<area>.enabled} — the report shows full detail; no shows only failures and known issues.</li>
 * </ul>
 * Framework no forces the report to {@link Mode#OFF}. DB additionally requires {@code db.available=yes}.
 */
public final class ValidationSwitches {

    public enum Area { UI, API, DB }

    /** OFF = not run, nothing reported; FAILURES_ONLY = run, report only failed/known-issue rows; FULL = run, report everything. */
    public enum Mode { OFF, FAILURES_ONLY, FULL }

    private ValidationSwitches() {}

    public static boolean run(Area area) {
        return mode(area) != Mode.OFF;
    }

    public static Mode mode(Area area) {
        FrameworkConfig c = ConfigFactory.getConfig();
        ReportConfig r = ConfigFactory.getReportConfig();
        boolean run;
        boolean detail;
        switch (area) {
            case UI -> { run = flag(c.validationUiEnabled(), "validation.ui.enabled"); detail = flag(r.validationUiDetail(), "report.validation.ui.enabled"); }
            case API -> { run = flag(c.validationApiEnabled(), "validation.api.enabled"); detail = flag(r.validationApiDetail(), "report.validation.api.enabled"); }
            default -> {
                run = flag(c.validationDbEnabled(), "validation.db.enabled") && DbAvailability.isAvailable();
                detail = flag(r.validationDbDetail(), "report.validation.db.enabled");
            }
        }
        return mode(run, detail);
    }

    /** Pure resolution rule, separated for unit testing. */
    public static Mode mode(boolean frameworkRun, boolean reportDetail) {
        if (!frameworkRun) return Mode.OFF;
        return reportDetail ? Mode.FULL : Mode.FAILURES_ONLY;
    }

    private static boolean flag(String raw, String key) {
        try {
            return DbAvailability.parseFlag(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(key + "='" + raw + "' is invalid — expected yes or no");
        }
    }
}
