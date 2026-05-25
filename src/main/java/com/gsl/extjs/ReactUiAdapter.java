package com.gsl.extjs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * UiFrameworkAdapter implementation for React (16+).
 * Uses the React fiber tree via __reactFiber$ DOM property keys.
 * Intentionally avoids __REACT_DEVTOOLS_GLOBAL_HOOK__ — absent in production builds.
 * Gracefully falls back to empty results when React is not present.
 */
public final class ReactUiAdapter implements UiFrameworkAdapter {

    private static final Logger LOG = LogManager.getLogger(ReactUiAdapter.class);

    @Override
    public String frameworkName() { return "React"; }

    @Override
    @SuppressWarnings("unchecked")
    public List<ComponentState> getComponentDiagnostics(WebDriver driver) {
        List<ComponentState> result = new ArrayList<>();
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String version = safeString(js.executeScript(
                    "try { return window.React && window.React.version ? window.React.version : 'unknown'; } catch(e) { return 'unknown'; }"));

            Object raw = js.executeScript("""
                try {
                    var seen = new Set();
                    var out  = [];
                    var nodes = document.querySelectorAll('*');
                    for (var i = 0; i < nodes.length && out.length < 10; i++) {
                        var el = nodes[i];
                        var fiberKey = Object.keys(el).find(k => k.startsWith('__reactFiber$'));
                        if (!fiberKey) continue;
                        var fiber = el[fiberKey];
                        if (!fiber || !fiber.type) continue;
                        var name = (typeof fiber.type === 'string')
                            ? null
                            : (fiber.type.displayName || fiber.type.name || null);
                        if (!name || seen.has(name)) continue;
                        seen.add(name);
                        var inDoc = document.body && document.body.contains(el);
                        out.push({
                            name:      name,
                            id:        fiber.key || '',
                            rendered:  true,
                            destroyed: false,
                            hidden:    el.offsetParent === null,
                            disabled:  el.disabled || false,
                            elPresent: inDoc
                        });
                    }
                    return out;
                } catch(e) { return []; }
                """);

            if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) continue;
                    Map<String, Object> map = (Map<String, Object>) m;
                    result.add(new ComponentState(
                            safeString(map.get("name")),
                            safeString(map.get("id")),
                            safeBoolean(map.get("rendered")),
                            safeBoolean(map.get("destroyed")),
                            safeBoolean(map.get("hidden")),
                            safeBoolean(map.get("disabled")),
                            safeBoolean(map.get("elPresent")),
                            version,
                            Map.of()
                    ));
                }
            }
        } catch (Exception e) {
            LOG.warn("[ReactUiAdapter] Component scan failed: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public String getActiveModule(WebDriver driver) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                    "return document.title || '';");
            return safeString(result);
        } catch (Exception e) {
            return "";
        }
    }

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

    private static String safeString(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o).trim();
        return "null".equalsIgnoreCase(s) ? "" : s;
    }

    private static boolean safeBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(String.valueOf(o));
    }
}
