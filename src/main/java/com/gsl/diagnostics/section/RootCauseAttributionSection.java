package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.diagnostics.section.frontend.BaseFrontendSection;
import com.gsl.diagnostics.section.frontend.FrontendPerformanceSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * DiagnosticSection that aggregates timing data from {@link CustomMetricsSection}
 * and {@link FrontendPerformanceSection} into a human-readable root-cause
 * attribution report.
 *
 * <p>For each test the section:
 * <ol>
 *   <li>Collects all timed contributors (common + framework-specific)</li>
 *   <li>Sorts by duration descending</li>
 *   <li>Uses First Contentful Paint as the total delay baseline</li>
 *   <li>Identifies the primary cause (&gt;40% of total delay) and
 *       secondary cause (&gt;15%)</li>
 *   <li>Classifies as FRONTEND_RENDERING, NETWORK, or MIXED</li>
 * </ol>
 *
 * <p>Rendered as a styled tree block with a coloured classification badge,
 * matching the report format defined in the enterprise diagnostics spec.
 */
public final class RootCauseAttributionSection implements DiagnosticSection {

    private static final String TD = "padding:3px 10px;border:1px solid #eee;";
    private static final String TH = "padding:4px 10px;border:1px solid #ddd;text-align:left;";

    private final String         framework;
    private final long           totalDelayMs;
    private final List<Contributor> contributors;
    private final String         primaryCause;
    private final String         secondaryCause;
    private final Classification classification;
    private final boolean        hasData;

    public RootCauseAttributionSection(CustomMetricsSection customMetrics,
                                       FrontendPerformanceSection frontendPerf) {

        if (customMetrics == null || frontendPerf == null) {
            this.framework      = "unknown";
            this.totalDelayMs   = 0;
            this.contributors   = List.of();
            this.primaryCause   = "";
            this.secondaryCause = "";
            this.classification = Classification.UNKNOWN;
            this.hasData        = false;
            return;
        }

        this.framework = frontendPerf.getFramework();

        // ── Collect contributors ──────────────────────────────────────────────
        List<Contributor> list = new ArrayList<>();

        // Common: long tasks
        long longTaskTotal = customMetrics.longTaskTotalMs();
        if (longTaskTotal > 0)
            list.add(new Contributor("Long Tasks (JS blocked)", longTaskTotal, false));

        // Common: overlay / loading — use actual component/selector name
        long overlayTotal = frontendPerf.totalOverlayDurationMs();
        if (overlayTotal > 0) {
            Map<String, Object> slowestOverlay = frontendPerf.getSlowestOverlayEntry();
            String overlayName = BaseFrontendSection.overlayLabel(slowestOverlay, framework);
            list.add(new Contributor(overlayName + " blocked UI", overlayTotal, false));
        }

        // Common: API timings (sum of all)
        long apiTotal = frontendPerf.getApiTimings().stream()
                .mapToLong(m -> toLong(m.get("durationMs"))).sum();
        if (apiTotal > 0)
            list.add(new Contributor("API Calls (total)", apiTotal, true));

        // Framework-specific contributors
        switch (framework) {
            case "extjs" -> {
                long layoutTotal = frontendPerf.totalLayoutTimeMs();
                if (layoutTotal > 0)
                    list.add(new Contributor("Layout Recalculation (Ext.layout)", layoutTotal, false));

                // Use the actual component name: "voucherGrid.render() blocked UI"
                Map<String, Object> slowest = frontendPerf.getSlowestComponentRender();
                long longestRender = frontendPerf.longestComponentRenderMs();
                if (longestRender > 0) {
                    String renderLabel = BaseFrontendSection.componentRenderLabel(slowest)
                            + " blocked UI";
                    list.add(new Contributor(renderLabel, longestRender, false));
                }
            }
            case "react" -> {
                // Use actual component name: "UserDashboard.render() blocked UI"
                Map<String, Object> slowestReact = frontendPerf.getSlowestReactRender();
                long reactRenderTotal = frontendPerf.longestComponentRenderMs();
                if (reactRenderTotal > 0) {
                    String renderLabel = BaseFrontendSection.reactRenderLabel(slowestReact)
                            + " blocked UI";
                    list.add(new Contributor(renderLabel, reactRenderTotal, false));
                }

                double cls = frontendPerf.totalLayoutShiftScore();
                if (cls > 0.01)
                    list.add(new Contributor("Cumulative Layout Shift — CLS " +
                            String.format("%.4f", cls), Math.round(cls * 1000), false));
            }
            case "angular" -> {
                long ngZoneTotal = frontendPerf.totalLayoutTimeMs();
                if (ngZoneTotal > 0)
                    list.add(new Contributor("NgZone instability blocked UI", ngZoneTotal, false));

                double cls = frontendPerf.totalLayoutShiftScore();
                if (cls > 0.01)
                    list.add(new Contributor("Cumulative Layout Shift — CLS " +
                            String.format("%.4f", cls), Math.round(cls * 1000), false));
            }
            default -> { /* nothing extra */ }
        }

        // Sort descending by duration
        list.sort(Comparator.comparingLong(Contributor::durationMs).reversed());
        this.contributors = list;

        // ── Determine total delay baseline (FCP) ─────────────────────────────
        this.totalDelayMs = customMetrics.firstContentfulPaintMs() > 0
                ? customMetrics.firstContentfulPaintMs()
                : contributors.stream().mapToLong(Contributor::durationMs).max().orElse(0);

        // ── Classify ─────────────────────────────────────────────────────────
        long renderMs  = overlayTotal + frontendPerf.longestComponentRenderMs()
                       + frontendPerf.totalLayoutTimeMs() + longTaskTotal;
        boolean heavyRender  = totalDelayMs > 0 && renderMs  > totalDelayMs * 0.6;
        boolean heavyNetwork = totalDelayMs > 0 && apiTotal  > totalDelayMs * 0.5
                               && longTaskTotal < 200;
        this.classification = heavyRender  ? Classification.FRONTEND_RENDERING
                            : heavyNetwork ? Classification.NETWORK
                            :                Classification.MIXED;

        // ── Primary / secondary cause labels ─────────────────────────────────
        String primary   = "";
        String secondary = "";
        if (!list.isEmpty()) {
            Contributor first = list.get(0);
            primary = first.isNetwork()
                    ? "Heavy network / API latency (" + first.label() + ")"
                    : "Heavy synchronous " + frameworkLabel() + " rendering (" + first.label() + ")";
            if (list.size() > 1) {
                Contributor second = list.get(1);
                secondary = second.isNetwork()
                        ? "API latency — " + second.label()
                        : "Rendering overhead — " + second.label();
            }
        }
        this.primaryCause   = primary;
        this.secondaryCause = secondary;
        this.hasData        = !list.isEmpty();
    }

    // ── DiagnosticSection ────────────────────────────────────────────────────

    @Override
    public String sectionTitle() {
        return "Root Cause Attribution — " + frameworkLabel();
    }

    @Override
    public boolean hasData() { return hasData; }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();

        // ── Classification badge ──────────────────────────────────────────────
        String badgeColor = switch (classification) {
            case FRONTEND_RENDERING -> "#cc0000";
            case NETWORK            -> "#cc6600";
            case MIXED              -> "#8B6914";
            default                 -> "#555555";
        };
        html.append("<div style='margin-bottom:8px;'>")
            .append("<span style='background:").append(badgeColor)
            .append(";color:white;padding:3px 10px;border-radius:3px;font-size:11px;font-weight:bold;'>")
            .append(classification.label())
            .append("</span>")
            .append("<span style='font-size:11px;color:#555;margin-left:8px;'>")
            .append(frameworkLabel()).append(" Application")
            .append("</span>")
            .append("</div>");

        // ── Summary line ──────────────────────────────────────────────────────
        if (totalDelayMs > 0) {
            html.append("<p style='font-size:12px;margin:4px 0 8px;'>")
                .append("<b>Total Delay (FCP baseline):</b> ").append(totalDelayMs).append(" ms")
                .append("</p>");
        }

        // ── Breakdown table ───────────────────────────────────────────────────
        html.append("<table style='border-collapse:collapse;font-size:12px;font-family:monospace;width:100%;'>")
            .append("<tr style='background:#f0f0f0;font-weight:bold;'>")
            .append("<th style='").append(TH).append("'>Contributor</th>")
            .append("<th style='").append(TH).append("'>Duration (ms)</th>")
            .append("<th style='").append(TH).append("'>% of Total</th>")
            .append("<th style='").append(TH).append("'>Role</th>")
            .append("</tr>");

        for (int i = 0; i < contributors.size(); i++) {
            Contributor c   = contributors.get(i);
            double pct      = totalDelayMs > 0 ? (c.durationMs() * 100.0 / totalDelayMs) : 0;
            boolean primary = i == 0;
            String rowColor = primary ? "#fff3f3" : "white";
            String dColor   = c.isNetwork() ? "#cc6600" : "#cc0000";
            String role     = primary          ? "← PRIMARY"
                            : c.isNetwork()    ? "Network"
                            : i == 1           ? "Secondary"
                                               : "Contributing";
            String prefix   = i == contributors.size() - 1 ? "└──" : "├──";
            html.append("<tr style='background:").append(rowColor).append(";'>")
                .append("<td style='").append(TD).append("'>").append(prefix).append(" ")
                .append(esc(c.label())).append("</td>")
                .append("<td style='").append(TD).append("color:").append(dColor)
                .append(";font-weight:bold;'>").append(c.durationMs()).append(" ms</td>")
                .append("<td style='").append(TD).append("'>")
                .append(String.format("%.1f%%", pct)).append("</td>")
                .append("<td style='").append(TD).append("color:")
                .append(primary ? "#cc0000" : "#555").append(";'>")
                .append(esc(role)).append("</td>")
                .append("</tr>");
        }
        html.append("</table>");

        // ── Cause summary ─────────────────────────────────────────────────────
        html.append("<div style='margin-top:8px;font-size:11px;font-family:monospace;")
            .append("border-left:3px solid ").append(badgeColor)
            .append(";padding-left:8px;'>");
        if (!primaryCause.isEmpty()) {
            html.append("<div><b>Primary&nbsp;&nbsp;:</b> ").append(esc(primaryCause)).append("</div>");
        }
        if (!secondaryCause.isEmpty()) {
            html.append("<div><b>Secondary:</b> ").append(esc(secondaryCause)).append("</div>");
        }
        html.append("<div><b>Class&nbsp;&nbsp;&nbsp;&nbsp;:</b> ").append(classification.label()).append("</div>");
        html.append("</div>");

        return html.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String frameworkLabel() {
        return switch (framework) {
            case "react"   -> "React";
            case "angular" -> "Angular";
            case "extjs"   -> "ExtJS";
            default        -> framework;
        };
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private record Contributor(String label, long durationMs, boolean isNetwork) {}

    private enum Classification {
        FRONTEND_RENDERING("FRONTEND_RENDERING — not network, not API"),
        NETWORK("NETWORK — backend / API latency"),
        MIXED("MIXED — rendering + network"),
        UNKNOWN("UNKNOWN");

        private final String label;
        Classification(String label) { this.label = label; }
        public String label() { return label; }
    }
}
