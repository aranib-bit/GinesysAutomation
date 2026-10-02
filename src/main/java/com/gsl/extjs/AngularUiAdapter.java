package com.gsl.extjs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link UiFrameworkAdapter} implementation for Angular (9+ Ivy) — uses {@code window.ng} (the Ivy
 * DevTools API) when available. Falls back to a DOM attribute scan ({@code [ng-version]},
 * {@code [_nghost]}) for production builds where {@code window.ng} is absent, logging a WARN when
 * falling back so it is visible in reports.
 *
 * <p>Siblings: {@link ExtJsUiAdapter}, {@link ReactUiAdapter}.
 */
public final class AngularUiAdapter implements UiFrameworkAdapter {

    private static final Logger LOG = LogManager.getLogger(AngularUiAdapter.class);

    /** {@inheritDoc} Always {@code "Angular"}. */
    @Override
    public String frameworkName() { return "Angular"; }

    /** {@inheritDoc} Tries {@code window.ng.getComponent} on each Angular root element first; falls back to a DOM attribute scan (logged as a WARN) when {@code window.ng} is unavailable. */
    @Override
    @SuppressWarnings("unchecked")
    public List<ComponentState> getComponentDiagnostics(WebDriver driver) {
        List<ComponentState> result = new ArrayList<>();
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String version = safeString(js.executeScript("""
                try {
                    var el = document.querySelector('[ng-version]');
                    return el ? el.getAttribute('ng-version') : 'unknown';
                } catch(e) { return 'unknown'; }
                """));

            // Try Ivy window.ng API first (dev builds and Angular DevMode)
            Object raw = js.executeScript("""
                try {
                    if (!window.ng) throw new Error('ng not available');
                    var roots = window.getAllAngularRootElements
                        ? window.getAllAngularRootElements()
                        : [document.querySelector('app-root')].filter(Boolean);
                    var out = [];
                    for (var i = 0; i < Math.min(roots.length, 10); i++) {
                        var el  = roots[i];
                        var cmp = window.ng.getComponent(el);
                        var inDoc = document.body && document.body.contains(el);
                        out.push({
                            name:      cmp ? cmp.constructor.name : el.tagName.toLowerCase(),
                            id:        el.getAttribute('id') || el.tagName.toLowerCase(),
                            rendered:  true,
                            destroyed: false,
                            hidden:    el.offsetParent === null,
                            disabled:  el.disabled || false,
                            elPresent: inDoc,
                            selector:  el.tagName.toLowerCase()
                        });
                    }
                    return out;
                } catch(e) { return null; }
                """);

            if (raw == null) {
                // Fallback: DOM attribute scan for production builds
                LOG.warn("[AngularUiAdapter] window.ng not available — using DOM fallback (production build)");
                raw = js.executeScript("""
                    try {
                        var els = document.querySelectorAll('[ng-version], app-root, [_nghost]');
                        var out = [];
                        els.forEach(function(el) {
                            if (out.length >= 10) return;
                            var inDoc = document.body && document.body.contains(el);
                            out.push({
                                name:      el.tagName.toLowerCase(),
                                id:        el.getAttribute('id') || el.tagName.toLowerCase(),
                                rendered:  true,
                                destroyed: false,
                                hidden:    el.offsetParent === null,
                                disabled:  false,
                                elPresent: inDoc,
                                selector:  el.tagName.toLowerCase()
                            });
                        });
                        return out;
                    } catch(e) { return []; }
                    """);
            }

            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) continue;
                    Map<String, Object> map = (Map<String, Object>) m;
                    String selector = safeString(map.get("selector"));
                    result.add(new ComponentState(
                            safeString(map.get("name")),
                            safeString(map.get("id")),
                            safeBoolean(map.get("rendered")),
                            safeBoolean(map.get("destroyed")),
                            safeBoolean(map.get("hidden")),
                            safeBoolean(map.get("disabled")),
                            safeBoolean(map.get("elPresent")),
                            version,
                            selector.isBlank() ? Map.of() : Map.of("selector", selector)
                    ));
                }
            }
        } catch (Exception e) {
            LOG.warn("[AngularUiAdapter] Component scan failed: {}", e.getMessage());
        }
        return result;
    }

    /** {@inheritDoc} Reads the {@code name} attribute of the page's {@code router-outlet}. */
    @Override
    public String getActiveModule(WebDriver driver) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript("""
                try {
                    var outlet = document.querySelector('router-outlet');
                    return outlet ? (outlet.getAttribute('name') || 'primary') : '';
                } catch(e) { return ''; }
                """);
            return safeString(result);
        } catch (Exception e) {
            return "";
        }
    }

    /** {@inheritDoc} Reads the element with {@code role="tab"} and {@code aria-selected="true"}. */
    @Override
    public String getActiveTab(WebDriver driver) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript("""
                try {
                    var tab = document.querySelector('[role="tab"][aria-selected="true"]');
                    return tab ? (tab.textContent || '').trim() : '';
                } catch(e) { return ''; }
                """);
            return safeString(result);
        } catch (Exception e) {
            return "";
        }
    }

    /** {@inheritDoc} Returns the current pathname + search + hash. */
    @Override
    public String getActiveRoute(WebDriver driver) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                    "return window.location.pathname + window.location.search + window.location.hash;");
            return safeString(result);
        } catch (Exception e) {
            return "";
        }
    }

    /** Normalizes a JS execution result to a trimmed string, treating {@code null}/the literal "null" as empty. */
    private static String safeString(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o).trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    /** Normalizes a JS execution result to a boolean. */
    private static boolean safeBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(o));
    }
}
