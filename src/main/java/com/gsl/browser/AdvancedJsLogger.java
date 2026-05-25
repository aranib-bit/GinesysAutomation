package com.gsl.browser;

import com.gsl.driver.DriverManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;

/**
 * Injects a JS shim that intercepts console.log/warn/error/info and window.onerror,
 * storing entries in window.testLogs[]. Each entry carries:
 *   type                  — 'CONSOLE' or 'JS_ERROR'
 *   level                 — 'log'|'warn'|'error'|'info' (CONSOLE) or 'error' (JS_ERROR)
 *   message               — joined argument string or error message
 *   stack                 — error.stack (JS_ERROR only, 'N/A' if unavailable)
 *   time                  — ISO-8601 timestamp string
 *   timestamp             — epoch millis (long)
 *   source                — 'console' or 'onerror'
 *   line                  — line number (JS_ERROR only, 0 otherwise)
 *   col                   — column number (JS_ERROR only, 0 otherwise)
 *   capturedAtStep        — value of window._fwCurrentStep at error fire time
 *   capturedAfterActionMs — ms since window._fwStepStartMs at error fire time (-1 if unknown)
 *   pageReadyState        — document.readyState at error fire time
 * A guard flag prevents double-injection on SPA navigations.
 * StepExecutor sets window._fwCurrentStep and window._fwStepStartMs before each action
 * so these are always populated when an error fires inside a step.
 */
public final class AdvancedJsLogger {

    private static final Logger LOG = LogManager.getLogger(AdvancedJsLogger.class);

    private static final String INJECTION_SCRIPT = """
            if (!window._testLogsInjected) {
                window._testLogsInjected = true;
                try {
                    var _saved = sessionStorage.getItem('_fwTestLogs');
                    window.testLogs = _saved ? JSON.parse(_saved) : [];
                } catch(e) { window.testLogs = []; }
                function _now() { return new Date().toISOString(); }
                var _orig = {
                    log:   console.log.bind(console),
                    warn:  console.warn.bind(console),
                    error: console.error.bind(console),
                    info:  console.info.bind(console)
                };
                ['log','warn','error','info'].forEach(function(level) {
                    console[level] = function() {
                        var msg = Array.from(arguments).join(' ');
                        window.testLogs.push({
                            type:      'CONSOLE',
                            level:     level,
                            message:   msg,
                            time:      _now(),
                            timestamp: Date.now(),
                            source:    'console'
                        });
                        _orig[level].apply(console, arguments);
                    };
                });
                window.onerror = function(msg, url, line, col, error) {
                    var stepMs = window._fwStepStartMs
                        ? (Date.now() - window._fwStepStartMs) : -1;
                    window.testLogs.push({
                        type:                 'JS_ERROR',
                        level:                'error',
                        message:              msg,
                        url:                  url   || 'N/A',
                        line:                 line  || 0,
                        col:                  col   || 0,
                        stack:                error ? error.stack : 'N/A',
                        time:                 _now(),
                        timestamp:            Date.now(),
                        source:               'onerror',
                        capturedAtStep:       window._fwCurrentStep  || 'unknown',
                        capturedAfterActionMs: stepMs,
                        pageReadyState:       document.readyState
                    });
                    return false;
                };
                window.addEventListener('beforeunload', function() {
                    try { sessionStorage.setItem('_fwTestLogs', JSON.stringify(window.testLogs)); } catch(e) {}
                });
            }
            """;

    private AdvancedJsLogger() {}

    /** Returns the raw injection script — used by CdpNetworkCapture for persistent registration. */
    public static String getScript() { return INJECTION_SCRIPT; }

    /** Clears accumulated logs and the sessionStorage backup — call at the start of each test. */
    public static void clearLogs() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
            js.executeScript(
                "window.testLogs = [];" +
                "try { sessionStorage.removeItem('_fwTestLogs'); } catch(e) {}");
        } catch (Exception e) {
            LOG.warn("[AdvancedJsLogger] Could not clear logs: {}", e.getMessage());
        }
    }

    public static void inject() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
            js.executeScript(INJECTION_SCRIPT);
            LOG.debug("[AdvancedJsLogger] JS logger injected.");
        } catch (Exception e) {
            LOG.warn("[AdvancedJsLogger] Could not inject JS logger: {}", e.getMessage());
        }
    }
}
