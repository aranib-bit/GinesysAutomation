package com.gsl.reports;

import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.RootCause;
import com.gsl.enums.ReportSeverity;
import com.gsl.reports.profile.RuntimeExecutionProfile;
import com.gsl.utils.TestContextManager;
import org.testng.ITestResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a compact failure-summary box as the first visible entry after the fail
 * status marker, so readers see the critical context immediately without scrolling
 * through the full diagnostic bundle.
 *
 * Rendered once per failed test from {@code TestListener.onTestFailure()} before
 * {@code FailureBundleOrchestrator.onFailure()} attaches the full evidence bundle.
 */
public final class FailureSummaryRenderer {

    private FailureSummaryRenderer() {}

    /** Backward-compatible entry point — renders the summary box with no root-cause banner. */
    public static void render(ITestResult result) {
        render(result, null);
    }

    /**
     * Renders the failure summary box, optionally enriched with an R-04 root-cause verdict.
     * When {@code rootCause} is non-null a color-coded "FAILURE ROOT CAUSE" banner is prepended
     * inside the box and Root Cause / Confidence rows are appended to the data table.
     */
    public static void render(ITestResult result, RootCause rootCause) {
        if (ExtentManager.getExtentTest() == null) return;

        Throwable t = result.getThrowable();
        String exType   = t != null ? t.getClass().getSimpleName()  : "Unknown";
        String exMsg    = t != null ? humanReadableMessage(t) : "No message";

        List<String> trail = ActionTrailBuffer.getForDisplay();
        String lastStep = trail.isEmpty() ? "—" : trail.get(trail.size() - 1);

        String browser  = safe(com.gsl.config.BrowserResolver.name());
        String env      = safe(ConfigFactory.getConfig().env());
        String tcId     = nvl(TestContextManager.getTestCaseId(), "—");
        String execId   = nvl(TestContextManager.getExecutionId(), "—");
        String thread   = Thread.currentThread().getName();

        String appUrl   = "—";
        RuntimeExecutionProfile profile = RuntimeProfileHolder.get();
        if (profile != null && profile.getExecution() != null) {
            appUrl = nvl(profile.getExecution().getApplicationUrl(), "—");
        }

        StringBuilder html = new StringBuilder();
        html.append("<div style='border:2px solid #c62828;border-radius:4px;padding:12px 14px;")
            .append("margin:6px 0 10px 0;background:#fff5f5;font-family:monospace;font-size:12px;'>");

        // ── R-04: color-coded root-cause banner (distinct from the performance
        //         "Root Cause Attribution" diagnostic node) ──
        if (rootCause != null) {
            String rcColor = severityColor(rootCause.severity());
            html.append("<div style='background:").append(rcColor)
                .append(";color:#fff;border-radius:3px;padding:8px 10px;margin-bottom:10px;'>")
                .append("<span style='font-weight:bold;font-size:13px;'>&#9632;&nbsp; FAILURE ROOT CAUSE: ")
                .append(esc(rootCause.category().label())).append("</span>")
                .append("<div style='font-weight:normal;font-size:11px;margin-top:5px;line-height:1.4;'>")
                .append(esc(rootCause.detail())).append("</div></div>");
        }

        html.append("<div style='color:#c62828;font-weight:bold;font-size:13px;margin-bottom:8px;'>")
            .append("&#9888;&nbsp; TEST FAILURE SUMMARY</div>")
            // Data table
            .append("<table style='border-collapse:collapse;width:100%;'>")
            .append(labelRow("Exception",    exType,                            "#c62828", true))
            .append(labelRow("Message",      exMsg,                             "#333",    false))
            .append(labelRow("Failed Step",  lastStep,                          "#333",    false))
            .append(labelRow("Test ID",      tcId,                              "#555",    false))
            .append(labelRow("Execution ID", execId,                            "#888",    false))
            .append(labelRow("Environment",  env + "  |  " + browser + "  |  Thread: " + thread, "#333", false))
            .append(labelRow("App URL",      appUrl,                            "#555",    false));

        if (rootCause != null) {
            html.append(labelRow("Root Cause",  rootCause.category().label(),
                                  severityColor(rootCause.severity()), true))
                .append(labelRow("Confidence",  rootCause.confidence(), "#555", false));
        }

        html.append("</table></div>");

        String htmlStr = html.toString();
        // Item 2 (Round 12): deliberately a flat top-level entry, not a child node. The bundled
        // ExtentReports Spark template renders all flat top-level logs BEFORE every child step
        // node regardless of real call order — a prior round switched this to
        // ExtentLogger.infoHtmlOnChildNode(...) specifically to stop that, but a child node's
        // position among its siblings IS its real creation order, and since this renders from
        // TestListener.onTestFailure() (after every real step node already exists), that made the
        // summary render dead LAST instead — confirmed via live screenshot and against an older
        // report predating that change. Reverted per explicit user confirmation: rendering ABOVE
        // every step node (near the top, right after the test's own header info) is the preferred,
        // more useful position for quickly seeing why a test failed.
        ExtentLogger.infoHtml(htmlStr);
    }

    /** Renders one {@code <tr>} of the summary's Field|Value data table, with {@code value}
     *  rendered in {@code valueColor} and bolded when {@code bold}. */
    private static String labelRow(String label, String value,
                                   String valueColor, boolean bold) {
        String b1 = bold ? "<b>" : "";
        String b2 = bold ? "</b>" : "";
        return "<tr>"
            + "<td style='padding:3px 10px 3px 0;color:#777;font-weight:bold;"
            + "white-space:nowrap;vertical-align:top;'>"
            + esc(label) + "</td>"
            + "<td style='padding:3px 0;color:" + valueColor + ";word-break:break-word;'>"
            + b1 + esc(value).replace("\n", "<br>") + b2 + "</td>"
            + "</tr>";
    }

    /** Maps a report severity to the banner/label color used across the failure summary. */
    private static String severityColor(ReportSeverity severity) {
        if (severity == null) return "#37474f";
        return switch (severity) {
            case CRITICAL -> "#c62828";
            case HIGH     -> "#e65100";
            case MEDIUM   -> "#1565c0";
            default       -> "#37474f";   // LOW / TRACE
        };
    }

    /**
     * Splits AssertJ {@code SoftAssertions.assertAll()}'s thrown message shape:
     * {@code "\r\nThe following N assertions failed:\n1) [description] \n<AssertJ noise>\n2) [description] \n..."}
     * on each numbered entry's leading marker ({@code "N) "}), so every entry's chunk (everything
     * up to the next marker) can be handled on its own — see {@link #extractBracketedDescription}
     * for why the description itself still needs picking out of that chunk.
     */
    private static final Pattern ENTRY_MARKER = Pattern.compile("(?m)^\\d+\\)\\s*");

    /** Hard ceiling on the fully-joined summary; see {@link #joinWithOverflowSummary}. */
    private static final int MAX_TOTAL_LEN = 2000;

    /** Per-entry cap — long enough for a real column-diff description, short enough that a dozen
     *  failures still fit in the box. */
    private static final int MAX_ENTRY_LEN = 200;

    /**
     * Builds a human-readable one-line failure summary from {@code t.getMessage()}.
     *
     * <p>For AssertJ's {@code SoftAssertionError} (thrown by {@code SoftAssertions.assertAll()}
     * when one or more staged assertions failed — see {@code VoucherSnapshotValidator}), the raw
     * message is AssertJ-internal noise wrapping each failure's real description in
     * {@code N) [description]}; this extracts and joins those descriptions instead of falling
     * through to {@link #firstLine}, which previously landed on an empty string here (the raw
     * message starts with a lone {@code \r} before its first {@code \n}, and {@code firstLine}
     * took everything before that first {@code \n} — i.e. nothing) and rendered as {@code "—"},
     * hiding the real DB-validation failure reasons from the report.
     *
     * <p>Two bugs this replaces: the previous single-shot {@code "\d+\)\s*\[(.*?)]"} regex was
     * non-greedy, so it stopped at the FIRST {@code ]} it found — since every real description
     * here already starts with its own {@code "[tableName] ..."} tag, that threw away everything
     * past the tag instead of the full column-diff detail. And the joined result was then
     * hard-cut at a global 350-char boundary with no regard for entry boundaries — exactly why a
     * real report showed a description sheared off mid-word as {@code "...[finpost"} with no
     * closing bracket. Splitting on the numbered marker first, then taking each chunk's own
     * {@code [...]} span up to its LAST {@code ]} (not its first), keeps a description's own
     * nested brackets intact; capping length per-entry (not on the final joined string) means
     * truncation, if it happens at all, only ever lands after a complete entry.
     */
    private static String humanReadableMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) return "—";

        // Combined multi-area failure (UI / Database / Snapshot): already a readable block per area.
        if (t instanceof com.gsl.validation.CombinedValidationError) {
            return msg;
        }

        String[] chunks = ENTRY_MARKER.split(msg);
        List<String> descriptions = new ArrayList<>();
        for (String chunk : chunks) {
            String description = extractBracketedDescription(chunk);
            if (description != null && !description.isEmpty()) {
                descriptions.add(truncate(description, MAX_ENTRY_LEN));
            }
        }
        if (!descriptions.isEmpty()) {
            return joinWithOverflowSummary(descriptions);
        }

        // Numbered entries without a [bracketed] tag (e.g. UI-contract failures): use each entry's
        // first line instead of falling back to the bare "The following N assertions failed:" header.
        for (int i = 1; i < chunks.length; i++) {
            String first = firstLine(chunks[i], MAX_ENTRY_LEN);
            if (!first.equals("—")) descriptions.add(first);
        }
        if (!descriptions.isEmpty()) {
            return joinWithOverflowSummary(descriptions);
        }

        return firstLine(msg, 350);
    }

    /**
     * Pulls one AssertJ numbered entry's {@code [description]} out of its chunk (the text
     * between this entry's {@code "N) "} marker and the next, per {@link #ENTRY_MARKER}). The
     * chunk's first line holds {@code "[description]"}; AssertJ's own "Expecting value to be
     * true but was false" noise typically follows on the next line, so only the first line is
     * considered. Taking from the first {@code '['} to the LAST {@code ']'} on that line (not
     * the first, as the old regex did) keeps a description containing its own nested brackets
     * intact instead of truncating to just the leading tag.
     */
    private static String extractBracketedDescription(String chunk) {
        if (chunk == null) return null;
        String firstLine = chunk.strip();
        int nl = firstLine.indexOf('\n');
        if (nl >= 0) firstLine = firstLine.substring(0, nl).stripTrailing();
        int open = firstLine.indexOf('[');
        int close = firstLine.lastIndexOf(']');
        if (open < 0 || close <= open) return null;
        return firstLine.substring(open + 1, close).trim();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }

    /**
     * Joins every (already per-entry-capped) description, stopping short of {@link
     * #MAX_TOTAL_LEN} rather than hard-cutting the joined string — any entries that don't fit are
     * summarized as a trailing count instead of being sheared off mid-entry.
     */
    private static String joinWithOverflowSummary(List<String> descriptions) {
        String header = descriptions.size() + " assertion(s) failed: ";
        StringBuilder sb = new StringBuilder(header);
        int shown = 0;
        for (String d : descriptions) {
            String candidate = (shown == 0 ? "" : "; ") + d;
            if (sb.length() + candidate.length() > MAX_TOTAL_LEN) break;
            sb.append(candidate);
            shown++;
        }
        int remaining = descriptions.size() - shown;
        if (remaining > 0) sb.append("; …(+").append(remaining).append(" more)");
        return sb.toString();
    }

    /** Returns the first non-blank line of a multi-line message, truncated to maxLen. Leading
     *  blank lines/whitespace (e.g. a lone {@code \r} before the real content, as some
     *  exception types prepend) are stripped first so a message isn't mistaken for empty. */
    private static String firstLine(String s, int maxLen) {
        if (s == null || s.isBlank()) return "—";
        String trimmed = s.strip();
        if (trimmed.isEmpty()) return "—";
        int nl = trimmed.indexOf('\n');
        String line = nl >= 0 ? trimmed.substring(0, nl).stripTrailing() : trimmed;
        return line.length() > maxLen ? line.substring(0, maxLen) + "…" : line;
    }

    private static String nvl(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s : fallback;
    }

    private static String safe(String s) {
        return (s != null && !s.isBlank()) ? s : "—";
    }

    private static String esc(String input) {
        if (input == null || input.isBlank()) return "—";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
