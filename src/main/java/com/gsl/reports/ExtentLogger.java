package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.markuputils.Markup;
import com.gsl.driver.DriverManager;
import com.gsl.enums.LogCategory;
import com.gsl.enums.ReportSeverity;
import com.gsl.utils.Selenium.ScreenshotUtils;

/**
 * Thin wrapper over the ExtentReports API — every method mirrors a log entry onto both the
 * current thread's main-report {@link ExtentTest} (from {@link ExtentManager#getExtentTest()})
 * and, when set, its per-shard counterpart (from {@link ExtentManager#getShardTest()}), so a
 * test's shard report never falls behind its main-report entry. All methods are no-ops when
 * there is no active test, so callers never need to null-check first.
 *
 * <p>{@link ReportLogger} is the higher-level façade most test/framework code should call
 * instead — it additionally routes to Log4j2 and applies category/severity badges. Call this
 * class directly only for Extent-only concerns (raw HTML, tables, child nodes) that
 * {@link ReportLogger} doesn't already wrap.
 */
public final class ExtentLogger {

    private ExtentLogger() {
    }

    /** PASS status entry on the current test (and shard test, if any). */
    public static void pass(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().pass(message);
        if (ExtentManager.getShardTest()  != null) ExtentManager.getShardTest().pass(message);
    }

    /** Attaches a screenshot to the CURRENT step's Extent node on PASS — the pass-side
     *  counterpart to {@link #fail}/{@link #failWithScreenshotPath}. Every other screenshot
     *  mechanism in this codebase is fail-only; this exists for
     *  {@code GridColumnMenuSmokeVerification}'s per-step debugging (temporary, per explicit
     *  user request) but is written generically enough for any test to reuse later. */
    public static void passWithScreenshot(String message) {
        if (ExtentManager.getExtentTest() == null) return;
        if (DriverManager.getDriver() != null) {
            try {
                String screenshotPath = ScreenshotUtils.saveScreenshot(message);
                ExtentManager.getExtentTest().pass(message,
                        MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
                if (ExtentManager.getShardTest() != null) {
                    ExtentManager.getShardTest().pass(message,
                            MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
                }
            } catch (org.openqa.selenium.WebDriverException wde) {
                String msg = message + " [screenshot unavailable: " + wde.getMessage().split("\n")[0] + "]";
                ExtentManager.getExtentTest().pass(msg);
                if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().pass(msg);
            } catch (Exception screenshotEx) {
                String msg = message + " [screenshot save failed: " + screenshotEx.getMessage() + "]";
                ExtentManager.getExtentTest().pass(msg);
                if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().pass(msg);
            }
        } else {
            ExtentManager.getExtentTest().pass(message);
            if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().pass(message);
        }
    }

    /** FAIL status entry with an auto-captured screenshot (when a live driver session exists) on
     *  the current test (and shard test, if any). */
    public static void fail(String message) {
        if (ExtentManager.getExtentTest() == null) return;
        if (DriverManager.getDriver() != null) {
            try {
                // File-on-disk capture: keeps only the path string in the live Extent model
                // (vs. ~1.3× the raw PNG bytes when embedded as base64).
                String screenshotPath = ScreenshotUtils.saveScreenshot(message);
                // Message logged now; the screenshot is attached at test end by ScreenshotPolicy
                // (report.screenshots.mode: LAST = only the final failure screenshot per test).
                ExtentManager.getExtentTest().fail(message);
                java.util.List<com.aventstack.extentreports.ExtentTest> targets = new java.util.ArrayList<>();
                targets.add(ExtentManager.getExtentTest());
                if (ExtentManager.getShardTest() != null) {
                    ExtentManager.getShardTest().fail(message);
                    targets.add(ExtentManager.getShardTest());
                }
                ScreenshotPolicy.defer(targets, message, screenshotPath, null);
            } catch (org.openqa.selenium.WebDriverException wde) {
                String msg = message + " [screenshot unavailable: " + wde.getMessage().split("\n")[0] + "]";
                ExtentManager.getExtentTest().fail(msg);
                if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().fail(msg);
            } catch (Exception screenshotEx) {
                String msg = message + " [screenshot save failed: " + screenshotEx.getMessage() + "]";
                ExtentManager.getExtentTest().fail(msg);
                if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().fail(msg);
            }
        } else {
            ExtentManager.getExtentTest().fail(message);
            if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().fail(message);
        }
    }

    /** Plain INFO entry on the current test (and shard test, if any). */
    public static void info(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().info(message);
        if (ExtentManager.getShardTest()  != null) ExtentManager.getShardTest().info(message);
    }

    /** Logs an explicit SKIP entry (amber) — use for data-unavailability skips, not failures. */
    public static void skip(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().skip(message);
        if (ExtentManager.getShardTest()  != null) ExtentManager.getShardTest().skip(message);
    }

    /** Plain amber WARNING entry (does not fail the test) on the current test (and shard test, if any). */
    public static void warning(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().warning(message);
        if (ExtentManager.getShardTest()  != null) ExtentManager.getShardTest().warning(message);
    }

    /** Amber WARNING status (does not fail the test) with a bracketed category/severity prefix
     *  AND an attached screenshot — the warning-side counterpart to {@link #fail}'s screenshot
     *  handling. Use when something recoverable just happened and the report needs visible
     *  evidence without the test being marked as failed.
     *  <p>Note: unlike {@link #infoCategorized}, this uses a plain-text bracket prefix (e.g.
     *  {@code "[MODULE][HIGH] ..."}) rather than the colored HTML badge — the underlying
     *  ExtentReports {@code warning(...)} API has no {@code (Markup, Media)} overload, only
     *  {@code (String, Media)}, so a Markup-rendered badge can't be combined with a screenshot
     *  on the same log entry. */
    public static void warningCategorizedWithScreenshot(LogCategory category, ReportSeverity severity, String message) {
        if (ExtentManager.getExtentTest() == null) return;
        String prefixed = "[" + category.name() + "][" + severity.name() + "] " + message;
        if (DriverManager.getDriver() != null) {
            try {
                String screenshotPath = ScreenshotUtils.saveScreenshot(message);
                ExtentManager.getExtentTest().warning(prefixed,
                        MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
                if (ExtentManager.getShardTest() != null) {
                    ExtentManager.getShardTest().warning(prefixed,
                            MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
                }
                return;
            } catch (org.openqa.selenium.WebDriverException wde) {
                String msg = prefixed + " [screenshot unavailable: " + wde.getMessage().split("\n")[0] + "]";
                ExtentManager.getExtentTest().warning(msg);
                if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().warning(msg);
                return;
            } catch (Exception screenshotEx) {
                String msg = prefixed + " [screenshot save failed: " + screenshotEx.getMessage() + "]";
                ExtentManager.getExtentTest().warning(msg);
                if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().warning(msg);
                return;
            }
        }
        ExtentManager.getExtentTest().warning(prefixed);
        if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().warning(prefixed);
    }

    /** Attaches a screenshot already held as a base64 string (e.g. captured before this Extent
     *  test existed) to a failure log entry; falls back to a plain fail entry if the media
     *  attachment itself throws. */
    public static void failWithBase64Screenshot(String message, String base64) {
        if (ExtentManager.getExtentTest() == null) return;
        try {
            ExtentManager.getExtentTest().fail(message,
                    MediaEntityBuilder.createScreenCaptureFromBase64String(base64).build());
        } catch (Exception e) {
            ExtentManager.getExtentTest().fail(message);
        }
    }

    /** Attaches a pre-saved screenshot file (by path) to a failure log entry. */
    public static void failWithScreenshotPath(String message, String filePath) {
        if (ExtentManager.getExtentTest() == null) return;
        try {
            ExtentManager.getExtentTest().fail(message,
                    MediaEntityBuilder.createScreenCaptureFromPath(filePath).build());
        } catch (Exception e) {
            ExtentManager.getExtentTest().fail(message);
        }
    }

    /** Attaches raw HTML as an info entry on the current test. No-op if no active test. */
    public static void infoHtml(String html) {
        if (ExtentManager.getExtentTest() == null) return;
        ExtentManager.getExtentTest().info((Markup) () -> html);
    }

    /**
     * P8: Creates a named child node on the current ExtentTest and renders the given HTML
     * inside it. The child node appears inline in the test step list (same style as
     * StepExecutor nodes), ensuring it always renders after the preceding steps.
     *
     * @param nodeName  display name for the child node (shown as the collapsible header)
     * @param innerHtml HTML content to render inside the node
     */
    public static void infoHtmlOnChildNode(String nodeName, String innerHtml) {
        ExtentTest parent = ExtentManager.getExtentTest();
        if (parent == null) return;
        ExtentTest child = parent.createNode(nodeName);
        child.info((Markup) () -> innerHtml);
    }

    /**
     * Creates a trailing named child node marked PASS — used for end-of-test bookkeeping (e.g.
     * "TEST PASSED") that would otherwise be logged as a flat top-level entry and, per the
     * bundled ExtentReports Spark template ({@code standard-content.ftl}), render BEFORE every
     * step node regardless of real timestamp (it renders all flat top-level logs in one block,
     * then all child nodes in a separate block). Creating a trailing node instead makes it sort
     * after the step nodes, matching real end-of-test timing — same idiom as
     * {@link #infoHtmlOnChildNode}'s P8 fix. Still mirrors the flat message onto the shard test,
     * which never receives child nodes of its own.
     */
    public static void passOnChildNode(String nodeName, String message) {
        ExtentTest parent = ExtentManager.getExtentTest();
        if (parent != null) parent.createNode(nodeName).pass(message);
        if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().pass(message);
    }

    /** {@link #passOnChildNode}'s FAIL counterpart. */
    public static void failOnChildNode(String nodeName, String message) {
        ExtentTest parent = ExtentManager.getExtentTest();
        if (parent != null) parent.createNode(nodeName).fail(message);
        if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().fail(message);
    }

    /** {@link #passOnChildNode}'s SKIP counterpart. */
    public static void skipOnChildNode(String nodeName, String message) {
        ExtentTest parent = ExtentManager.getExtentTest();
        if (parent != null) parent.createNode(nodeName).skip(message);
        if (ExtentManager.getShardTest() != null) ExtentManager.getShardTest().skip(message);
    }

    /** Renders {@code table} ({@code table[0]} = header row, the rest data rows) as a styled
     *  scrollable HTML table on the current test — see {@link #buildScrollableTableHtml}. */
    public static void infoTable(String[][] table) {
        if (ExtentManager.getExtentTest() == null) return;
        ExtentManager.getExtentTest().info(buildScrollableTable(table));
    }

    /** Renders a bold label line followed by the styled table as a single Info entry. */
    public static void infoLabeledTable(String label, String[][] table) {
        if (ExtentManager.getExtentTest() == null) return;
        String html = "<p style='font-size:12px;font-weight:500;margin:0 0 5px;'>"
                + escapeHtml(label) + "</p>"
                + buildScrollableTableHtml(table);
        ExtentManager.getExtentTest().info((Markup) () -> html);
    }

    // ── Categorized logging with color-coded badges ────────────────────────────

    /**
     * Logs a message prefixed with a colored category badge.
     * Severity defaults to MEDIUM — no severity badge is shown.
     */
    public static void infoCategorized(LogCategory category, String message) {
        infoCategorized(category, ReportSeverity.MEDIUM, message);
    }

    /**
     * Logs a message prefixed with colored category and (non-MEDIUM) severity badges.
     * MEDIUM severity is the silent default — its badge is suppressed to avoid noise.
     *
     * <p>Suppressed from the Extent HTML report when {@code category.isDebugCategory()} is true
     * and {@code report.debugLogs.extentVisible} is off (the default) — see {@link LogCategory}.
     * File logging via {@code ReportLogger#loggerFor} happens independently, before this method
     * is ever called, and is unaffected either way.
     */
    public static void infoCategorized(LogCategory category, ReportSeverity severity, String message) {
        if (ExtentManager.getExtentTest() == null) return;
        if (category.isDebugCategory() && !com.gsl.config.ConfigFactory.getReportConfig().debugLogsExtentVisible()) return;
        String htmlStr = buildCategorizedHtml(category, severity, message);
        ExtentManager.getExtentTest().info((Markup) () -> htmlStr);
    }

    /**
     * Logs a message prefixed with colored category AND {@link com.gsl.enums.PopupOutcome} badges
     * — the popup-specific counterpart to {@link #infoCategorized(LogCategory, ReportSeverity, String)},
     * for a category (currently only {@code LogCategory.POPUP}) whose own debug/business flag
     * carries no useful signal once every sub-outcome shares one parent category. Visibility gates
     * on {@code outcome.isDebugCategory()} instead of {@code category.isDebugCategory()} — see
     * {@link com.gsl.enums.PopupOutcome}.
     */
    public static void infoCategorized(LogCategory category, com.gsl.enums.PopupOutcome outcome, String message) {
        if (ExtentManager.getExtentTest() == null) return;
        if (outcome.isDebugCategory() && !com.gsl.config.ConfigFactory.getReportConfig().debugLogsExtentVisible()) return;
        String htmlStr = buildPopupHtml(category, outcome, message);
        ExtentManager.getExtentTest().info((Markup) () -> htmlStr);
    }

    /**
     * Logs a message prefixed with a colored category badge, keeping Extent's native
     * PASS status (green) on the node rather than an INFO entry — used for step-level
     * outcome markers that should still roll the test/step up as passed.
     *
     * <p>Same debug/business gating as {@link #infoCategorized} — e.g. {@code TEST_STEP_STATUS}
     * (StepExecutor's "Step passed" marker) is business, always shown; {@code RECOVERY}'s
     * "dashboard recovered after refresh" detail is debug, hidden by default. Gating this out
     * only skips the log entry itself — it does not affect whether the step/test actually passed,
     * since Extent's overall status is driven by whether a FAIL entry was ever logged, not by
     * this specific PASS marker existing.
     */
    public static void passCategorized(LogCategory category, String message) {
        if (ExtentManager.getExtentTest() == null) return;
        if (category.isDebugCategory() && !com.gsl.config.ConfigFactory.getReportConfig().debugLogsExtentVisible()) return;
        String htmlStr = buildCategorizedHtml(category, ReportSeverity.MEDIUM, message);
        ExtentManager.getExtentTest().pass((Markup) () -> htmlStr);
    }

    /**
     * Logs a message prefixed with a colored category badge, keeping Extent's native FAIL status
     * (red) on the node — the FAIL-status counterpart to {@link #passCategorized}, used so a
     * categorized check's failure side carries the same badge its pass side does (e.g.
     * {@link com.gsl.pages.VerifiablePage}'s {@code VERIFICATION} confirmations).
     *
     * <p><b>Deliberately does NOT check {@code category.isDebugCategory()}</b> — unlike every
     * other categorized method here, a FAIL entry is never gated behind the business/debug
     * toggle, regardless of the category it's tagged with. Hiding a failure by default would be
     * actively harmful; only passing/informational noise is ever a legitimate candidate for the
     * business-only view to hide.
     */
    public static void failCategorized(LogCategory category, String message) {
        if (ExtentManager.getExtentTest() == null) return;
        String htmlStr = buildCategorizedHtml(category, ReportSeverity.MEDIUM, message);
        ExtentManager.getExtentTest().fail((Markup) () -> htmlStr);
    }

    private static String buildCategorizedHtml(LogCategory category, ReportSeverity severity, String message) {
        StringBuilder html = new StringBuilder();
        String catColor = categoryColor(category);
        html.append("<span style='background:").append(catColor)
            .append(";color:white;padding:1px 7px;border-radius:3px;")
            .append("font-size:10px;font-weight:bold;margin-right:5px;vertical-align:middle;'>")
            .append(category.name()).append("</span>");
        if (severity != ReportSeverity.MEDIUM) {
            String sevColor = severityColor(severity);
            html.append("<span style='background:").append(sevColor)
                .append(";color:white;padding:1px 6px;border-radius:3px;")
                .append("font-size:10px;font-weight:bold;margin-right:5px;vertical-align:middle;'>")
                .append(severity.name()).append("</span>");
        }
        html.append("<span style='vertical-align:middle;'>").append(escapeHtml(message)).append("</span>");
        return html.toString();
    }

    /** {@link #buildCategorizedHtml}'s counterpart for a {@link com.gsl.enums.PopupOutcome}
     *  sub-badge instead of a {@link ReportSeverity} one — see
     *  {@link #infoCategorized(LogCategory, com.gsl.enums.PopupOutcome, String)}. */
    private static String buildPopupHtml(LogCategory category, com.gsl.enums.PopupOutcome outcome, String message) {
        StringBuilder html = new StringBuilder();
        String catColor = categoryColor(category);
        html.append("<span style='background:").append(catColor)
            .append(";color:white;padding:1px 7px;border-radius:3px;")
            .append("font-size:10px;font-weight:bold;margin-right:5px;vertical-align:middle;'>")
            .append(category.name()).append("</span>");
        String outcomeColor = popupOutcomeColor(outcome);
        html.append("<span style='background:").append(outcomeColor)
            .append(";color:white;padding:1px 6px;border-radius:3px;")
            .append("font-size:10px;font-weight:bold;margin-right:5px;vertical-align:middle;'>")
            .append(outcome.name()).append("</span>");
        html.append("<span style='vertical-align:middle;'>").append(escapeHtml(message)).append("</span>");
        return html.toString();
    }

    /**
     * One badge color per {@link LogCategory} — each hex is commented with its human-readable
     * name (Material Design palette naming, where it matches one) so a reader adding a new
     * category can see at a glance which colors are already taken instead of decoding hex by eye.
     */
    private static String categoryColor(LogCategory cat) {
        return switch (cat) {
            case BUSINESS   -> "#1565c0"; // Blue 800
            case UI_ACTION  -> "#00695c"; // Teal 800
            case FIELD_ENTRY -> "#00897b"; // Teal 600
            case FIELD_VALIDATION -> "#0288d1"; // Light Blue 700 — deliberately distinct from
                                                 // FIELD_ENTRY's green (Item P)
            case VALIDATION -> "#2e7d32"; // Green 800
            case DB         -> "#283593"; // Indigo 800
            case NETWORK    -> "#6a1b9a"; // Purple 800
            case JS         -> "#e65100"; // Orange 900
            case PERF       -> "#bf360c"; // Deep Orange 900
            case FRAMEWORK  -> "#424242"; // Grey 800
            case RCA        -> "#b71c1c"; // Red 900
            case SESSION    -> "#00838f"; // Cyan 800
            case NAVIGATION -> "#558b2f"; // Light Green 800
            case RECOVERY   -> "#ad1457"; // Pink 800
            case MODULE           -> "#3949ab"; // Indigo 600
            case SUB_MODULE       -> "#6d4c41"; // Brown 600
            case MENU             -> "#0277bd"; // Light Blue 800
            case SUB_MENU         -> "#00acc1"; // Cyan 600
            case TEST_STEP_STATUS -> "#757575"; // Grey 600
            case CONTEXT_SWITCH   -> "#5e35b1"; // Deep Purple 600
            case DATA_BACKFILL    -> "#d84315"; // Deep Orange 800
            case VERIFICATION     -> "#00796b"; // Teal 700
            case LOADER_WAIT      -> "#455a64"; // Blue Grey 700
            case POPUP            -> "#6a4c93"; // Muted Purple/Plum (custom — not a standard Material swatch)
        };
    }

    /** Badge color for a {@link com.gsl.enums.PopupOutcome} sub-category badge — see
     *  {@link #infoCategorized(LogCategory, com.gsl.enums.PopupOutcome, String)}. */
    private static String popupOutcomeColor(com.gsl.enums.PopupOutcome outcome) {
        return switch (outcome) {
            case EXPECTED   -> "#827717";
            case UNEXPECTED -> "#d32f2f";
            case MAYBE      -> "#f57f17";
        };
    }

    /** One badge color per {@link ReportSeverity} — see {@link #categoryColor}'s own Javadoc for
     *  why these are commented with human-readable names. */
    private static String severityColor(ReportSeverity sev) {
        return switch (sev) {
            case CRITICAL -> "#b71c1c";
            case HIGH     -> "#e65100";
            case MEDIUM   -> "#1565c0";
            case LOW      -> "#616161";
            case TRACE    -> "#9e9e9e";
        };
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ── Table rendering ────────────────────────────────────────────────────────

    /** Wraps {@link #buildScrollableTableHtml}'s output as a lazily-evaluated {@link Markup}. */
    private static Markup buildScrollableTable(String[][] table) {
        String html = buildScrollableTableHtml(table);
        return () -> html;
    }

    /** Renders {@code table} as an HTML table with a bordered/scrollable wrapper — {@code
     *  table[0]} styled as the header row (light-blue background), every subsequent row as data;
     *  a {@code null} cell renders as "—". Shared by {@link #infoTable} and
     *  {@link #infoLabeledTable}. */
    private static String buildScrollableTableHtml(String[][] table) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='overflow-x:auto;background:#f8fbff;border-left:3px solid #6aacf0;padding:6px 10px;'>")
          .append("<table style='border-collapse:collapse;font-size:11px;font-family:monospace;table-layout:auto'>");
        for (int r = 0; r < table.length; r++) {
            sb.append("<tr>");
            for (String cell : table[r]) {
                if (r == 0) {
                    sb.append("<th style='border:1px solid #6aacf0;padding:4px 8px;background:#e8f4fd;font-weight:500;color:#0d47a1;word-break:break-word;overflow-wrap:anywhere'>")
                      .append(cell).append("</th>");
                } else {
                    sb.append("<td style='border:1px solid #b3d4f5;padding:4px 8px;word-break:break-word;overflow-wrap:anywhere'>")
                      .append(cell == null ? "—" : cell).append("</td>");
                }
            }
            sb.append("</tr>");
        }
        sb.append("</table></div>");
        return sb.toString();
    }
}

