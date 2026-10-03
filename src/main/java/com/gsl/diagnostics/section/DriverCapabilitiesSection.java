package com.gsl.diagnostics.section;

import com.gsl.config.TargetOsResolver;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.reports.BrowserFingerprint;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Captures browser session metadata from WebDriver capabilities at the moment of
 * test failure: browser name/version, platform, session ID, and remote grid URL
 * (when applicable). Renders as a compact key/value table in the DiagnosticReport.
 */
public final class DriverCapabilitiesSection implements DiagnosticSection {

    private final Map<String, String> caps = new LinkedHashMap<>();

    public DriverCapabilitiesSection(WebDriver driver) {
        if (driver == null) return;
        try {
            // The driver may be wrapped by EventFiringDecorator; unwrap to reach the real driver.
            WebDriver unwrapped = driver;
            while (unwrapped instanceof WrapsDriver) {
                unwrapped = ((WrapsDriver) unwrapped).getWrappedDriver();
            }

            if (unwrapped instanceof HasCapabilities) {
                Capabilities c = ((HasCapabilities) unwrapped).getCapabilities();
                put("browserName",    c.getBrowserName());
                put("browserVersion", String.valueOf(c.getBrowserVersion()));
                put("platformName",   String.valueOf(c.getPlatformName()));
            }
            if (unwrapped instanceof RemoteWebDriver) {
                String sessionId = ((RemoteWebDriver) unwrapped).getSessionId().toString();
                put("sessionId", sessionId);
                // Fingerprint was recorded when the session was created (Driver.initDriver)
                BrowserFingerprint f = BrowserFingerprint.forSession(sessionId);
                if (f != null) {
                    put("driverVersion", f.driverVersion());
                }
            }
            put("targetOs", TargetOsResolver.name());
        } catch (Exception e) {
            put("error", "Could not read capabilities: " + e.getMessage());
        }
    }

    @Override
    public String sectionTitle() {
        return "Driver Capabilities";
    }

    @Override
    public String renderHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<table style='border-collapse:collapse;font-size:11px;font-family:monospace;'>");
        caps.forEach((k, v) -> sb
                .append("<tr>")
                .append("<td style='padding:2px 8px;border:1px solid #ddd;font-weight:bold;background:#f8f8f8;'>")
                .append(esc(k)).append("</td>")
                .append("<td style='padding:2px 8px;border:1px solid #ddd;'>")
                .append(esc(v)).append("</td>")
                .append("</tr>"));
        sb.append("</table>");
        return sb.toString();
    }

    @Override
    public boolean hasData() {
        return !caps.isEmpty();
    }

    private void put(String key, String value) {
        if (value != null && !value.isBlank() && !value.equalsIgnoreCase("null")) {
            caps.put(key, value);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
