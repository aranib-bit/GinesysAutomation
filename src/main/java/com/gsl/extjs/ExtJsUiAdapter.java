package com.gsl.extjs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link UiFrameworkAdapter} implementation for Ext.js (Sencha ExtJS 6/7) — the framework this
 * application is built on today, and the {@link UiFrameworkAdapterFactory} default. All
 * diagnostics are extracted via JavaScript executed in the browser through
 * {@code Ext.ComponentQuery}. Gracefully returns empty results if Ext is not present on the page.
 *
 * <p>Siblings: {@link ReactUiAdapter}, {@link AngularUiAdapter}.
 */
public final class ExtJsUiAdapter implements UiFrameworkAdapter {

    private static final Logger LOG = LogManager.getLogger(ExtJsUiAdapter.class);

    /** {@inheritDoc} Always {@code "ExtJS"}. */
    @Override
    public String frameworkName() { return "ExtJS"; }

    /** {@inheritDoc} Queries {@code Ext.ComponentQuery.query('*')} for up to the first 10 components. */
    @Override
    @SuppressWarnings("unchecked")
    public List<ComponentState> getComponentDiagnostics(WebDriver driver) {
        List<ComponentState> result = new ArrayList<>();
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String version = safeString(js.executeScript(
                    "try { return Ext.getVersion().version; } catch(e) { return 'unknown'; }"));

            Object raw = js.executeScript("""
                try {
                    var cmps = Ext.ComponentQuery.query('*');
                    var out = [];
                    for (var i = 0; i < Math.min(cmps.length, 10); i++) {
                        var c = cmps[i];
                        out.push({
                            name:      c.xtype      || 'unknown',
                            id:        c.itemId     || c.id || '',
                            rendered:  !!c.rendered,
                            destroyed: !!c.destroyed,
                            hidden:    !!c.hidden,
                            disabled:  !!c.disabled,
                            elPresent: (c.el != null)
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
            LOG.warn("[ExtJsUiAdapter] Component scan failed: {}", e.getMessage());
        }
        return result;
    }

    /** {@inheritDoc} Reads the top-level viewport child's {@code title}/{@code xtype}. */
    @Override
    public String getActiveModule(WebDriver driver) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript("""
                try {
                    var vp = Ext.ComponentQuery.query('viewport > *');
                    return vp.length > 0 ? (vp[0].title || vp[0].xtype || '') : '';
                } catch(e) { return ''; }
                """);
            return safeString(result);
        } catch (Exception e) {
            return "";
        }
    }

    /** {@inheritDoc} Reads the active tab of the first {@code tabpanel} found on the page. */
    @Override
    public String getActiveTab(WebDriver driver) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript("""
                try {
                    var tp = Ext.ComponentQuery.query('tabpanel');
                    if (tp.length > 0) {
                        var tab = tp[0].getActiveTab();
                        return tab ? (tab.title || tab.xtype || '') : '';
                    }
                    return '';
                } catch(e) { return ''; }
                """);
            return safeString(result);
        } catch (Exception e) {
            return "";
        }
    }

    /** {@inheritDoc} Returns the URL hash — ExtJS routing here is hash-based. */
    @Override
    public String getActiveRoute(WebDriver driver) {
        try {
            Object result = ((JavascriptExecutor) driver).executeScript(
                    "return window.location.hash || '';");
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
