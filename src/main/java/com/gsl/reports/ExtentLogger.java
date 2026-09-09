package com.gsl.reports;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.markuputils.Markup;
import com.gsl.driver.DriverManager;
import com.gsl.enums.LogCategory;
import com.gsl.enums.ReportSeverity;
import com.gsl.utils.Selenium.ScreenshotUtils;

public final class ExtentLogger {

    private ExtentLogger() {
    }

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

    public static void fail(String message) {
        if (ExtentManager.getExtentTest() == null) return;
        if (DriverManager.getDriver() != null) {
            try {
                // File-on-disk capture: keeps only the path string in the live Extent model
                // (vs. ~1.3× the raw PNG bytes when embedded as base64).
                String screenshotPath = ScreenshotUtils.saveScreenshot(message);
                ExtentManager.getExtentTest().fail(message,
                        MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
                if (ExtentManager.getShardTest() != null) {
                    ExtentManager.getShardTest().fail(message,
                            MediaEntityBuilder.createScreenCaptureFromPath(screenshotPath).build());
                }
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

    public static void info(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().info(message);
        if (ExtentManager.getShardTest()  != null) ExtentManager.getShardTest().info(message);
    }

    /** Logs an explicit SKIP entry (amber) — use for data-unavailability skips, not failures. */
    public static void skip(String message) {
        if (ExtentManager.getExtentTest() != null) ExtentManager.getExtentTest().skip(message);
        if (ExtentManager.getShardTest()  != null) ExtentManager.getShardTest().skip(message);
    }

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

    private static String categoryColor(LogCategory cat) {
        return switch (cat) {
            case BUSINESS   -> "#1565c0";
            case UI_ACTION  -> "#00695c";
            case VALIDATION -> "#2e7d32";
            case DB         -> "#283593";
            case NETWORK    -> "#6a1b9a";
            case JS         -> "#e65100";
            case PERF       -> "#bf360c";
            case FRAMEWORK  -> "#424242";
            case RCA        -> "#b71c1c";
            case SESSION    -> "#00838f";
            case NAVIGATION -> "#558b2f";
            case RECOVERY   -> "#ad1457";
            case MODULE           -> "#3949ab";
            case SUB_MODULE       -> "#6d4c41";
            case MENU             -> "#0277bd";
            case SUB_MENU         -> "#00acc1";
            case TEST_STEP_STATUS -> "#757575";
            case CONTEXT_SWITCH   -> "#5e35b1";
            case DATA_BACKFILL    -> "#d84315";
            case VERIFICATION     -> "#00796b";
            case LOADER_WAIT      -> "#455a64";
            case POPUP            -> "#6a4c93";
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

    private static Markup buildScrollableTable(String[][] table) {
        String html = buildScrollableTableHtml(table);
        return () -> html;
    }

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

