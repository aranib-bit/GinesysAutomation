package com.gsl.utils.Selenium;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.Markup;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.utils.Log;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Explains an intermittent UI failure from the LIVE page instead of waiting longer: when a step
 * fails, this reads (in one script) what is actually on screen — visible loading masks, windows /
 * dialogs with their titles and text, open dropdown lists with their items (or "no data" text), cell
 * editors and their value, a login error message, whether Ext.Ajax still has requests in flight and
 * which element has focus. The summary goes to the log and to the failing step's report node.
 *
 * <p>Generic mechanics only (no voucher/domain knowledge). Limits and the on/off switch come from
 * {@code diagnostics.properties} ({@code diagnostics.page.state.*}); capture is best effort and can
 * never fail or slow a test beyond one script call.
 */
public final class PageStateDiagnostics {

    private PageStateDiagnostics() {}

    /** Throwable whose failure was already captured on this thread — nested steps rethrow the same
     *  instance, so the innermost (most accurate) snapshot is the only one recorded. */
    private static final ThreadLocal<Throwable> LAST_CAPTURED = new ThreadLocal<>();

    private static final String SCRIPT = """
            var maxItems = arguments[0], maxChars = arguments[1];
            function vis(e){ var r=e.getBoundingClientRect(), cs=getComputedStyle(e);
                return cs.display!=='none' && cs.visibility!=='hidden' && r.width>0 && r.height>0; }
            function txt(e){ return ((e&&(e.innerText||e.textContent))||'').replace(/\\s+/g,' ').trim().substring(0,maxChars); }
            function take(a){ return a.slice(0,maxItems); }
            function all(sel){ return [].slice.call(document.querySelectorAll(sel)).filter(vis); }
            var out=[];
            out.push('URL: '+location.href);
            var masks=all('.x-mask');
            out.push('Visible masks: '+masks.length);
            take(masks).forEach(function(m){ var r=m.getBoundingClientRect();
                out.push('  mask '+(m.id||'')+' z='+getComputedStyle(m).zIndex+' '+Math.round(r.width)+'x'+Math.round(r.height)+' "'+txt(m)+'"'); });
            var wins=all('.x-window');
            out.push('Visible windows/dialogs: '+wins.length);
            take(wins).forEach(function(w){ var t=w.querySelector('[class*=winTitle],.x-title-text,.x-header-text');
                out.push('  window '+(w.id||'')+' title="'+txt(t)+'" z='+getComputedStyle(w).zIndex+' text="'+txt(w)+'"'); });
            var bl=all('.x-boundlist');
            out.push('Open dropdown lists: '+bl.length);
            take(bl).forEach(function(b){ var items=[].slice.call(b.querySelectorAll('.x-boundlist-item'));
                out.push('  list '+b.id+' items='+items.length+' ['+take(items).map(txt).join(' | ')+'] emptyText="'+txt(b.querySelector('.x-boundlist-empty'))+'"'); });
            var eds=all('.x-editor, .x-grid-editor');
            out.push('Visible cell editors: '+eds.length);
            take(eds).forEach(function(e){ var i=e.querySelector('input,textarea');
                var col='?'; try{ var c=window.Ext&&Ext.getCmp&&Ext.getCmp(e.id); var cx=c&&c.context;
                    if(cx&&cx.column) col=(cx.column.dataIndex||'')+'/'+(cx.column.text||'')+' row='+cx.rowIdx+' grid='+(cx.grid&&cx.grid.itemId||''); }catch(x){}
                out.push('  editor '+(e.id||'')+' column='+col+' value="'+(i?i.value:'')+'" text="'+txt(e)+'"'); });
            var errs=[].slice.call(document.querySelectorAll('*')).filter(function(e){
                return e.children.length===0 && /^Error:/.test((e.textContent||'').trim()) && vis(e); });
            if(errs.length) out.push('Error text on page: '+take(errs).map(txt).join(' || '));
            try{ if(window.Ext && Ext.Ajax) out.push('Ext.Ajax.isLoading: '+Ext.Ajax.isLoading()); }catch(x){}
            var ae=document.activeElement;
            out.push('Active element: '+(ae?ae.tagName+'#'+ae.id+'.'+String(ae.className||'').substring(0,80):'none'));
            return out.join('\\n');
            """;

    /**
     * Records the live page state for a failing step, once per failure: skipped when disabled, when
     * there is no browser, or when {@code failure} was already captured by a nested step.
     *
     * @param label  what failed, e.g. {@code "Step 'Login to Application' failed"}
     * @param node   the report node to attach the summary to (may be null: log only)
     * @return the captured summary, or {@code null} when nothing was captured
     */
    public static String captureOnce(Throwable failure, String label, ExtentTest node) {
        try {
            if (!ConfigFactory.getDiagnosticsConfig().diagnosticsPageStateEnabled()) return null;
            if (failure != null && LAST_CAPTURED.get() == failure) return null;
            WebDriver driver = DriverManager.getDriver();
            if (driver == null) return null;
            Object result = ((JavascriptExecutor) driver).executeScript(SCRIPT,
                    ConfigFactory.getDiagnosticsConfig().diagnosticsPageStateMaxItems(),
                    ConfigFactory.getDiagnosticsConfig().diagnosticsPageStateMaxTextChars());
            if (failure != null) LAST_CAPTURED.set(failure);
            String summary = String.valueOf(result);
            Log.warn("[PageState] {}\n{}", label, summary);
            if (node != null) {
                String html = "<div style='font-family:monospace;font-size:12px'><b>Page state — "
                        + escape(label) + "</b><pre style='white-space:pre-wrap;margin:4px 0'>"
                        + escape(summary) + "</pre></div>";
                node.info((Markup) () -> html);
            }
            return summary;
        } catch (RuntimeException e) {
            Log.debug("[PageState] snapshot skipped: {}", e.getMessage());
            return null;
        }
    }

    /** The first visible leaf element text starting with {@code "Error:"} (e.g. the login window's
     *  "Error: The user name or password is incorrect."), or {@code null} when there is none / no browser. */
    public static String visibleErrorText() {
        try {
            WebDriver driver = DriverManager.getDriver();
            if (driver == null) return null;
            Object r = ((JavascriptExecutor) driver).executeScript("""
                    var els=[].slice.call(document.querySelectorAll('*')).filter(function(e){
                        if(e.children.length!==0||!/^Error:/.test((e.textContent||'').trim())) return false;
                        var b=e.getBoundingClientRect(), c=getComputedStyle(e);
                        return b.width>0&&b.height>0&&c.display!=='none'&&c.visibility!=='hidden'; });
                    return els.length? els[0].textContent.replace(/\\s+/g,' ').trim() : null;""");
            return r == null ? null : String.valueOf(r);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Forgets the last-captured failure (call at test start so a recycled thread starts clean). */
    public static void reset() {
        LAST_CAPTURED.remove();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
