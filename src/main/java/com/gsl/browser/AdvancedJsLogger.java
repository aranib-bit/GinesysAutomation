package com.gsl.browser;

import com.gsl.driver.DriverManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;

/**
 * Injects a JS shim that intercepts console.log/warn/error/info and window.onerror,
 * storing entries in window.testLogs[]. Each entry carries:
 *   type      — 'CONSOLE' or 'JS_ERROR'
 *   level     — 'log'|'warn'|'error'|'info' (CONSOLE) or 'error' (JS_ERROR)
 *   message   — joined argument string or error message
 *   stack     — error.stack (JS_ERROR only, 'N/A' if unavailable)
 *   time      — ISO-8601 timestamp string
 *   timestamp — epoch millis (long)
 *   source    — 'console' or 'onerror'
 * A guard flag prevents double-injection on SPA navigations.
 */
public final class AdvancedJsLogger {

    private static final Logger LOG = LogManager.getLogger(AdvancedJsLogger.class);

    private static final String INJECTION_SCRIPT = """
            if (!window._testLogsInjected) {
                window._testLogsInjected = true;
                window.testLogs = window.testLogs || [];
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
                    window.testLogs.push({
                        type:      'JS_ERROR',
                        level:     'error',
                        message:   msg,
                        url:       url  || 'N/A',
                        line:      line || 0,
                        col:       col  || 0,
                        stack:     error ? error.stack : 'N/A',
                        time:      _now(),
                        timestamp: Date.now(),
                        source:    'onerror'
                    });
                    return false;
                };
            }
            """;

    private AdvancedJsLogger() {}

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
