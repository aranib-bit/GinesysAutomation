package com.gsl.diagnostics.section;

import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.extjs.ComponentState;
import com.gsl.extjs.UiFrameworkAdapter;
import com.gsl.extjs.UiFrameworkAdapterFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;

import java.util.List;

/**
 * DiagnosticSection that renders live UI component state from the browser.
 * Framework is selected by application.type in config.properties (ExtJS | React | Angular).
 * This class is framework-agnostic — it delegates entirely to UiFrameworkAdapter.
 */
public final class UiComponentSection implements DiagnosticSection {

    private static final Logger LOG = LogManager.getLogger(UiComponentSection.class);

    private final List<ComponentState> components;
    private final String               activeModule;
    private final String               activeTab;
    private final String               activeRoute;
    private final String               frameworkName;

    public UiComponentSection(WebDriver driver) {
        UiFrameworkAdapter adapter = UiFrameworkAdapterFactory.get();
        this.frameworkName = adapter.frameworkName();

        List<ComponentState> cmps   = List.of();
        String               module = "";
        String               tab    = "";
        String               route  = "";
        try {
            cmps   = adapter.getComponentDiagnostics(driver);
            module = adapter.getActiveModule(driver);
            tab    = adapter.getActiveTab(driver);
            route  = adapter.getActiveRoute(driver);
        } catch (Exception e) {
            LOG.warn("[UiComponentSection] Adapter {} failed: {}", frameworkName, e.getMessage());
        }
        this.components   = cmps;
        this.activeModule = module;
        this.activeTab    = tab;
        this.activeRoute  = route;
    }

    @Override
    public String sectionTitle() {
        return "UI Components — " + frameworkName + " (" + components.size() + " components)";
    }

    @Override
    public boolean hasData() {
        return !components.isEmpty()
            || !activeModule.isBlank()
            || !activeRoute.isBlank();
    }

    @Override
    public String renderHtml() {
        StringBuilder html = new StringBuilder();

        // Context header bar
        html.append("<table style='border-collapse:collapse;font-size:11px;font-family:monospace;margin-bottom:8px;'>")
            .append("<tr style='background:#e8f0fe;font-weight:bold;'>")
            .append("<th style='").append(TH).append("'>Active Module</th>")
            .append("<th style='").append(TH).append("'>Active Tab</th>")
            .append("<th style='").append(TH).append("'>Route</th>")
            .append("<th style='").append(TH).append("'>Framework</th>")
            .append("</tr>")
            .append("<tr>")
            .append("<td style='").append(TD).append("'>").append(esc(or(activeModule, "—"))).append("</td>")
            .append("<td style='").append(TD).append("'>").append(esc(or(activeTab, "—"))).append("</td>")
            .append("<td style='").append(TD).append("'>").append(esc(or(activeRoute, "—"))).append("</td>")
            .append("<td style='").append(TD).append("'>").append(esc(frameworkName)).append("</td>")
            .append("</tr>")
            .append("</table>");

        if (components.isEmpty()) {
            html.append("<div style='color:#888;font-style:italic;'>No components detected.</div>");
            return html.toString();
        }

        // Component table
        html.append("<table style='width:100%;border-collapse:collapse;font-size:11px;font-family:monospace;'>")
            .append("<tr style='background:#f0f0f0;font-weight:bold;'>")
            .append("<th style='").append(TH).append("'>Name / xtype</th>")
            .append("<th style='").append(TH).append("'>ID / itemId</th>")
            .append("<th style='").append(TH).append("'>Rendered</th>")
            .append("<th style='").append(TH).append("'>Destroyed</th>")
            .append("<th style='").append(TH).append("'>Hidden</th>")
            .append("<th style='").append(TH).append("'>Disabled</th>")
            .append("<th style='").append(TH).append("'>el Present</th>")
            .append("</tr>");

        for (ComponentState c : components) {
            // Row background: red if destroyed, orange if not rendered
            String rowBg = c.destroyed() ? "#fff0f0"
                         : !c.rendered() ? "#fff8f0"
                         : "#ffffff";

            html.append("<tr style='background:").append(rowBg).append(";'>")
                .append(td(esc(or(c.name(), "—")), null))
                .append(td(esc(or(c.id(), "—")), null))
                .append(boolCell(c.rendered(),  "rendered",  false))
                .append(boolCell(c.destroyed(), "destroyed", true))
                .append(boolCell(c.hidden(),    "hidden",    false))
                .append(boolCell(c.disabled(),  "disabled",  false))
                .append(boolCell(c.elPresent(), "elPresent", false))
                .append("</tr>");

            // Extra map row (framework-specific extras)
            if (!c.extra().isEmpty()) {
                html.append("<tr style='background:#fafafa;'><td colspan='7' style='")
                    .append(TD).append("padding-left:24px;font-size:10px;color:#555;'>");
                c.extra().forEach((k, v) ->
                    html.append("<b>").append(esc(k)).append(":</b> ").append(esc(v)).append(" &nbsp; "));
                html.append("</td></tr>");
            }
        }

        html.append("</table>");
        return html.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final String TH = "padding:4px 8px;border:1px solid #ddd;text-align:left;";
    private static final String TD = "padding:3px 8px;border:1px solid #eee;";

    private static String td(String content, String extraStyle) {
        String style = TD + (extraStyle != null ? extraStyle : "");
        return "<td style='" + style + "'>" + content + "</td>";
    }

    /**
     * Renders a boolean cell.
     * badWhenTrue=true  → value true   is red (e.g. destroyed=true is bad)
     * badWhenTrue=false → value false  is orange (e.g. rendered=false is bad)
     */
    private static String boolCell(boolean value, String label, boolean badWhenTrue) {
        String color;
        if (badWhenTrue) {
            color = value ? "#cc0000" : "#006600";
        } else {
            color = value ? "#006600" : "#cc6600";
        }
        String icon = value ? "&#10003;" : "&#10007;";
        return "<td style='" + TD + "color:" + color + ";font-weight:bold;'>"
             + icon + " " + value + "</td>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String or(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
