package com.gsl.diagnostics.section;

import com.gsl.config.ConfigFactory;
import com.gsl.diagnostics.DiagnosticSection;
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

    private final List<String> captured = new ArrayList<>();

    public ActivePopupSection(WebDriver driver) {
        if (driver == null) return;
        if (!ConfigFactory.getConfig().diagnosticsActivePopupEnabled()) return;

        captureJsAlert(driver);
        captureUiModals(driver);
    }

    @Override
    public String sectionTitle() {
        return "Active Popups (" + captured.size() + ")";
    }

    @Override
    public String renderHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family:monospace;font-size:11px;'>");
        for (String entry : captured) {
            sb.append("<div style='padding:2px 0;border-bottom:1px solid #eee;'>")
              .append(esc(entry))
              .append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    @Override
    public boolean hasData() {
        return !captured.isEmpty();
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
            String selector = ConfigFactory.getConfig().diagnosticsPopupSelector();
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
