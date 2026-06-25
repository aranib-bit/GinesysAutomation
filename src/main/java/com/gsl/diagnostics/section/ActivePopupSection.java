package com.gsl.diagnostics.section;

import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.DiagnosticSection;
import com.gsl.reports.PopupTrailBuffer;
import com.gsl.utils.Log;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects and captures any active popups or modals at the moment of test failure:
 *   1. JavaScript alert/confirm/prompt (via driver.switchTo().alert())
 *   2. Visible ExtJS or custom UI modals via a CSS selector (configurable)
 *
 * Config keys:
 *   diagnostics.active.popup.enabled  = true
 *   diagnostics.popup.selector        = .x-window-body
 */
public final class ActivePopupSection implements DiagnosticSection {

    private final List<String> captured  = new ArrayList<>();  // live snapshot
    private final List<String> trailAll  = new ArrayList<>();  // popups during the test

    public ActivePopupSection(WebDriver driver) {
        trailAll.addAll(PopupTrailBuffer.getAll());

        if (driver != null && ConfigFactory.getDiagnosticsConfig().diagnosticsActivePopupEnabled()) {
            captureJsAlert(driver);
            captureUiModals(driver);
        }
    }

    @Override
    public String sectionTitle() {
        int total = captured.size() + trailAll.size();
        return "Active Popups (" + total + ")";
    }

    @Override
    public String renderHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:monospace;font-size:11px;'>");

        if (!captured.isEmpty()) {
            sb.append("<div style='font-weight:bold;padding:4px 0 2px;color:#c0392b;'>Currently visible:</div>");
            for (String entry : captured) {
                sb.append("<div style='padding:2px 0;border-bottom:1px solid #eee;'>")
                  .append(esc(entry)).append("</div>");
            }
        }

        if (!trailAll.isEmpty()) {
            sb.append("<div style='font-weight:bold;padding:6px 0 2px;color:#2c3e50;'>Handled during test:</div>");
            for (String entry : trailAll) {
                sb.append("<div style='padding:2px 0;border-bottom:1px solid #eee;color:#555;'>")
                  .append(esc(entry)).append("</div>");
            }
        }

        sb.append("</div>");
        return sb.toString();
    }

    @Override
    public boolean hasData() {
        return !captured.isEmpty() || !trailAll.isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void captureJsAlert(WebDriver driver) {
        try {
            String text = driver.switchTo().alert().getText();
            driver.switchTo().defaultContent();
            captured.add("[JS Alert] " + text);
        } catch (NoAlertPresentException ignored) {
            // no alert
        } catch (Exception e) {
            Log.debug("[ActivePopupSection] Alert check failed: {}", e.getMessage());
        }
    }

    private void captureUiModals(WebDriver driver) {
        try {
            String selector = ConfigFactory.getDiagnosticsConfig().diagnosticsPopupSelector();
            if (selector == null || selector.isBlank()) return;

            String script =
                "var els = document.querySelectorAll('" + selector.replace("'", "\\'") + "');" +
                "var results = [];" +
                "for (var i = 0; i < els.length; i++) {" +
                "  var el = els[i];" +
                "  var style = window.getComputedStyle(el);" +
                "  if (style.display !== 'none' && style.visibility !== 'hidden' && el.offsetParent !== null) {" +
                "    results.push(el.innerText ? el.innerText.trim().substring(0, 200) : '[empty]');" +
                "  }" +
                "}" +
                "return results;";

            @SuppressWarnings("unchecked")
            List<String> modals = (List<String>) ((JavascriptExecutor) driver).executeScript(script);
            if (modals != null) {
                for (String text : modals) {
                    if (text != null && !text.isBlank()) {
                        captured.add("[UI Modal] " + text);
                    }
                }
            }
        } catch (Exception e) {
            Log.debug("[ActivePopupSection] Modal detection failed: {}", e.getMessage());
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
