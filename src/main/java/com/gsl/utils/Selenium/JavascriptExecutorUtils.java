package com.gsl.utils.Selenium;

import com.gsl.config.ConfigFactory;
import com.gsl.driver.DriverManager;
import com.gsl.exceptions.SubLedgerNotFoundException;
import com.gsl.pages.enums.Inventory.Product_Definition.GroupHierarchyType;
import com.gsl.reports.ExtentLogger;
import com.gsl.reports.ReportLogger;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class JavascriptExecutorUtils {

    private JavascriptExecutorUtils() {
    }

    private static JavascriptExecutor getJsExecutor() {
        return (JavascriptExecutor) DriverManager.getDriver();
    }

    private static WebElement findElement(By by) {
        return DriverManager.getDriver().findElement(by);
    }

    public static void scrollToElement(WebElement element) {
        getJsExecutor().executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", element);
    }

    public static void scrollToElement(By by) {
        scrollToElement(findElement(by));
    }

    public static void scrollIntoViewForced(WebElement element) {
        getJsExecutor().executeScript("arguments[0].scrollIntoView(true);", element);
    }

    /**
     * Resets the SelectDocument popup grid scroll position back to the top
     * and fires the 'scroll' event so ExtJS re-renders the first set of rows.
     * Called at the start of every selectDocument() call so that:
     *   - 1st document  : grid is already at top — harmless no-op in effect.
     *   - 2nd+ document : grid was left at the previous document's position;
     *                     this resets it so the search always starts fresh
     *                     from row 0 regardless of prior selections.
     */
    public static void resetGridScrollToTop() {
        String script =
            // Primary: scope to the active ExtJS popup window so the background
            // page's grid view is never picked up by mistake.
            "try {" +
            "  var win = Ext.WindowManager.getActive();" +
            "  if (win) {" +
            "    var g = win.down('gridpanel') || win.down('grid');" +
            "    if (g) {" +
            "      var v = g.getView().el.dom;" +
            "      v.scrollTop = 0;" +
            "      v.dispatchEvent(new Event('scroll', { bubbles: true }));" +
            "      return;" +
            "    }" +
            "  }" +
            "} catch(e) {}" +
            // Fallback: original behaviour when no active window is found.
            "var views = document.querySelectorAll('.x-grid-view');" +
            "for (var i = 0; i < views.length; i++) {" +
            "  var v = views[i];" +
            "  if (v.offsetHeight > 0) {" +
            "    v.scrollTop = 0;" +
            "    v.dispatchEvent(new Event('scroll', { bubbles: true }));" +
            "    return;" +
            "  }" +
            "}";
        getJsExecutor().executeScript(script);
    }

    /**
     * PRIMARY scroll strategy for SelectDocument popup.
     *
     * Searches all visible ExtJS gridpanel stores for a record whose any
     * field value equals documentNumber.  When found it calculates the exact
     * scrollTop (rowIndex × rowHeight) and jumps there in ONE step — so the
     * same code works correctly whether the grid has 50 rows or 60,000+.
     *
     * Returns the scrollTop position that was set, or -1 if the record was
     * not found in any loaded store (e.g. a BufferedStore whose target page
     * has not been fetched yet).
     */
    public static long jumpGridToDocument(String documentNumber) {
        String script =
            "try {" +
            "  var docNum = arguments[0];" +

            // Primary: use the active ExtJS popup window to get the correct grid.
            "  var win = Ext.WindowManager.getActive();" +
            "  var grids = [];" +
            "  if (win) {" +
            "    grids = win.query('gridpanel');" +
            "    if (!grids.length) grids = win.query('grid');" +
            "  }" +
            // Fallback: search all gridpanels (original behaviour).
            "  if (!grids.length) grids = Ext.ComponentQuery.query('gridpanel');" +

            "  for (var g = 0; g < grids.length; g++) {" +
            "    var store = grids[g].getStore();" +
            "    if (!store || store.getCount() === 0) continue;" +
            "    var idx = store.findBy(function(rec) {" +
            "      var d = rec.getData();" +
            "      for (var k in d) {" +
            "        if (d[k] !== null && d[k] !== undefined &&" +
            "            String(d[k]).trim() === docNum) return true;" +
            "      }" +
            "      return false;" +
            "    });" +
            "    if (idx < 0) continue;" +

            // Scroll the correct view: active-window grid view preferred.
            "    var v = grids[g].getView().el.dom;" +
            "    var rows = v.querySelectorAll('tr.x-grid-row');" +
            "    var rowH = rows.length > 0 ? rows[0].offsetHeight : 26;" +
            "    var target = idx * rowH;" +
            "    v.scrollTop = target;" +
            "    v.dispatchEvent(new Event('scroll', { bubbles: true }));" +
            "    return target;" +
            "  }" +
            "  return -1;" +
            "} catch(e) { return -1; }";
        Object result = getJsExecutor().executeScript(script, documentNumber);
        return result != null ? ((Number) result).longValue() : -1;
    }

    /**
     * FALLBACK scroll strategy — used when jumpGridToDocument() returns -1
     * (BufferedStore: target record page not yet loaded in memory).
     *
     * Scrolls the grid view by a PROPORTIONAL step (scrollHeight / 15) so
     * the same number of iterations covers the full height regardless of
     * record count — 500 rows or 60,000 rows both complete in ≤ 15 steps.
     *
     * Returns the new scrollTop, or -1 if no scrollable grid view was found.
     */
    public static long scrollSelectDocumentGridDown() {
        String script =
            // Primary: scope to the active ExtJS popup window.
            "try {" +
            "  var win = Ext.WindowManager.getActive();" +
            "  if (win) {" +
            "    var g = win.down('gridpanel') || win.down('grid');" +
            "    if (g) {" +
            "      var v = g.getView().el.dom;" +
            "      var step = Math.max(300, Math.ceil(v.scrollHeight / 15));" +
            "      v.scrollTop = Math.min(v.scrollTop + step, v.scrollHeight);" +
            "      v.dispatchEvent(new Event('scroll', { bubbles: true }));" +
            "      return v.scrollTop;" +
            "    }" +
            "  }" +
            "} catch(e) {}" +
            // Fallback: original behaviour.
            "var views = document.querySelectorAll('.x-grid-view');" +
            "for (var i = 0; i < views.length; i++) {" +
            "  var v = views[i];" +
            "  if (v.offsetHeight === 0) continue;" +
            "  var step = Math.max(300, Math.ceil(v.scrollHeight / 15));" +
            "  v.scrollTop = Math.min(v.scrollTop + step, v.scrollHeight);" +
            "  v.dispatchEvent(new Event('scroll', { bubbles: true }));" +
            "  return v.scrollTop;" +
            "}" +
            "return -1;";
        Object result = getJsExecutor().executeScript(script);
        return result != null ? ((Number) result).longValue() : -1;
    }

    /**
     * LAST-RESORT strategy for SelectDocument popup.
     *
     * Uses the ExtJS public API view.focusRow(index) to scroll the virtualized
     * grid to the target record. Unlike jumpGridToDocument() which uses manual
     * scrollTop math (rowIndex x rowHeight), focusRow() handles variable row
     * heights and triggers proper ExtJS row rendering internally.
     *
     * Returns the record index (>= 0) if found and focused, or -1 if not found.
     * Called only when the three existing strategies (direct find, jumpGrid,
     * proportional scroll) have all been exhausted.
     */
    public static long focusGridRowByDocument(String documentNumber) {
        String script =
            "try {" +
            "  var docNum = arguments[0];" +

            // Primary: use the active ExtJS popup window.
            "  var win = Ext.WindowManager.getActive();" +
            "  var grids = [];" +
            "  if (win) {" +
            "    grids = win.query('gridpanel');" +
            "    if (!grids.length) grids = win.query('grid');" +
            "  }" +
            // Fallback: all visible gridpanels (original behaviour).
            "  if (!grids.length) grids = Ext.ComponentQuery.query('gridpanel');" +

            "  for (var g = 0; g < grids.length; g++) {" +
            "    var grid = grids[g];" +
            "    if (!grid.isVisible()) continue;" +
            "    var store = grid.getStore();" +
            "    if (!store || store.getCount() === 0) continue;" +
            "    var idx = store.findBy(function(rec) {" +
            "      var d = rec.getData();" +
            "      for (var k in d) {" +
            "        if (d[k] !== null && d[k] !== undefined &&" +
            "            String(d[k]).trim() === docNum) return true;" +
            "      }" +
            "      return false;" +
            "    });" +
            "    if (idx < 0) continue;" +
            "    grid.getView().focusRow(idx);" +
            "    return idx;" +
            "  }" +
            "  return -1;" +
            "} catch(e) { return -1; }";
        Object result = getJsExecutor().executeScript(script, documentNumber);
        return result != null ? ((Number) result).longValue() : -1;
    }

    /**
     * Returns a diagnostic string for the active window's grid:
     * active-window title, store.getCount(), store.getTotalCount(),
     * store.isLoading(), scrollHeight, scrollTop, and visible mask classes.
     * Used purely for WARN-level logging to diagnose loader/scroll issues.
     */
    public static String getGridDiagnostics() {
        String script =
            "try {" +
            "  var result = {};" +
            "  var win = Ext.WindowManager.getActive();" +
            "  result.activeWin = win ? (win.title || win.id || 'untitled') : 'none';" +
            "  if (win) {" +
            "    var g = win.down('gridpanel') || win.down('grid');" +
            "    if (g) {" +
            "      var store = g.getStore();" +
            "      result.storeCount    = store ? store.getCount() : -1;" +
            "      result.storeTotalCount = store ? (store.getTotalCount ? store.getTotalCount() : -1) : -1;" +
            "      result.storeLoading  = store ? store.isLoading() : 'n/a';" +
            "      var v = g.getView().el.dom;" +
            "      result.scrollHeight  = v.scrollHeight;" +
            "      result.scrollTop     = v.scrollTop;" +
            "    } else { result.grid = 'not found'; }" +
            "  }" +
            "  var masks = document.querySelectorAll('.x-mask,.x-mask-fixed,.x-mask-loading,.x-mask-msg');" +
            "  var visibleMasks = [];" +
            "  for (var i = 0; i < masks.length; i++) {" +
            "    if (masks[i].offsetHeight > 0) visibleMasks.push(masks[i].className);" +
            "  }" +
            "  result.visibleMasks = visibleMasks;" +
            "  return JSON.stringify(result);" +
            "} catch(e) { return 'diagnostics-error: ' + e.message; }";
        Object result = getJsExecutor().executeScript(script);
        return result != null ? result.toString() : "null";
    }

    public static void click(WebElement element) {
        getJsExecutor().executeScript("arguments[0].click();", element);
    }

    public static void click(By by) {
        click(findElement(by));
    }

    public static void highlight(WebElement element) {
        getJsExecutor().executeScript("arguments[0].style.border='3px solid red'", element);
    }

    public static void highlight(By by) {
        highlight(findElement(by));
    }

    public static void unhighlight(WebElement element) {
        getJsExecutor().executeScript("arguments[0].style.border='0px'", element);
    }

    public static void unhighlight(By by) {
        unhighlight(findElement(by));
    }

    public static Object executeScript(String script, Object... args) {
        return getJsExecutor().executeScript(script, args);
    }

    public static void waitForExtJS() {
        new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout())).until(
                driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "return !!(window.Ext && window.Ext.isReady && " +
                                "!window.Ext.Ajax.isLoading() && " +
                                "window.Ext.ComponentQuery.query('[isMasked=true]').length === 0);"
                )
        );
    }

    public static void waitForExtLayout() {
        new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout())).until(
                driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "return window.Ext !== undefined && Ext.ComponentManager.all.getCount() > 0;"
                )
        );
    }

    public static void waitForStoreLoad(String storeId) {
        new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(extJsTimeout())).until(
                driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "var store = Ext.getStore('" + storeId + "');" +
                        "return store ? !store.isLoading() : true;"
                )
        );
    }

    public static void waitForPageLoad() {
        new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(30)).until(
                driver -> ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete")
        );
    }

    /**
     * Debug: Print all currently loading/masked components (xtype, itemId, id).
     */
    @SuppressWarnings("unchecked")
    public static void logLoadingComponents() {
        List<Map<String, Object>> components = (List<Map<String, Object>>) getJsExecutor().executeScript(
                "if (window.Ext) {" +
                        "var comps = [];" +
                        "Ext.ComponentQuery.query('[isMasked=true]').forEach(function(c) {" +
                        "  comps.push({" +
                        "    xtype: c.getXType ? c.getXType() : null," +
                        "    itemId: c.getItemId ? c.getItemId() : null," +
                        "    id: c.getId ? c.getId() : null" +
                        "  });" +
                        "});" +
                        "return comps;" +
                        "} else { return []; }"
        );

        if (components.isEmpty()) {
            System.out.println("No masked/loading components found.");
        } else {
            components.forEach(c ->
                    System.out.println("Loading Component - xtype: " + c.get("xtype") +
                            ", itemId: " + c.get("itemId") +
                            ", id: " + c.get("id"))
            );
        }
    }


    public static void waitForComponentReady() {
        new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(20))
                .until(driver -> {
                    Object result = ((JavascriptExecutor) driver).executeScript(
                            "if (window.Ext && window.Ext.ComponentQuery) {" +
                                    "var comps = Ext.ComponentQuery.query('[isMasked=true]');" +
                                    "return comps.map(function(c){ " +
                                    "   return {" +
                                    "       xtype: c.xtype || '', " +
                                    "       itemId: c.itemId || '', " +
                                    "       id: c.id || ''" +
                                    "   };" +
                                    "});" +
                                    "} else { return []; }"
                    );

                    if (result instanceof List) {
                        List<?> list = (List<?>) result;

                        if (list.isEmpty()) {
                            System.out.println("✅ No masked/loading components found.");
                            return true;  // <---- FIXED ✅ return true when ready
                        } else {
                            for (Object obj : list) {
                                if (obj instanceof Map) {
                                    Map<?, ?> comp = (Map<?, ?>) obj;
                                    System.out.println("⏳ Still loading -> xtype: " + comp.get("xtype")
                                            + ", itemId: " + comp.get("itemId")
                                            + ", id: " + comp.get("id"));
                                }
                            }
                            return false; // not ready yet
                        }
                    }
                    return true; // if script didn’t return a list, continue
                });
    }


    @SuppressWarnings("unchecked")
    public static List<String> getTreePanelTexts() {

        String script =
                "var result = [];" +
                        "Ext.ComponentQuery.query('treepanel').forEach(function(tree, treeIndex) {" +
                        "  tree.getStore().each(function(node) {" +
                        "    result.push('Tree ' + treeIndex + ': Depth ' + node.getDepth() + ' → ' + node.get('text'));" +
                        "  });" +
                        "});" +
                        "return result;";

//        String script =
//                "var result = [];" +
//                        "Ext.ComponentQuery.query('treepanel').forEach(function(tree, treeIndex) {" +
//                        "  tree.getStore().each(function(node) {" +
//                        "    result.push('Tree ' + treeIndex + ': ' + node.get('text'));" +
//                        "  });" +
//                        "});" +
//                        "return result;";

        return (List<String>) getJsExecutor().executeScript(script);
    }

    /**
     * Updates a node in TreePanel by old text → new text.
     *
     * @param oldText Current text to find
     * @param newText New text to update
     */
    public static void updateTreeNodeText(String oldText, String newText) {
        String safeOld = oldText.replace("'", "\\'");
        String safeNew = newText.replace("'", "\\'");
        System.out.println("SafeOld " + safeOld);
        System.out.println("SafeNew " + safeNew);
        String script = String.format(
                "var tree = Ext.ComponentQuery.query('treepanel')[1];" +
                        "var store = tree.getStore();" +
                        "var node = store.findNode('text', '%s');" +
                        "if (node) { node.set('text', '%s'); node.commit(); }",
                safeOld, safeNew
        );
        getJsExecutor().executeScript(script);
    }

    /**
     * Returns true if the tree contains a given node text.
     */
    public static boolean doesTreeContainText(GroupHierarchyType type, String nodeText) {
        return getTreePanelTexts().stream()
                .anyMatch(line -> line.contains("Depth " + type.getDepth() + " → " + nodeText));
    }



    /**
     * Count occurrences of a node text inside TreePanel.
     */
    public static long countTreeOccurrences(GroupHierarchyType type) {
        return getTreePanelTexts().stream()
                .filter(line -> line.contains("Depth " + type.getDepth() + " → " + type.getDisplayName()))
                .count();
    }


    public static void logTreePanelNodes() {
        String script =
                "var result = [];" +
                        "Ext.ComponentQuery.query('treepanel').forEach(function(tree) {" +
                        "    var root = tree.getStore().getRootNode();" +
                        "    root.eachChild(function(node) {" +
                        "        result.push(node.getData());" +
                        "    });" +
                        "});" +
                        "return result;";  // return to Selenium

        JavascriptExecutor jsExecutor = (JavascriptExecutor) DriverManager.getDriver();
        Object output = jsExecutor.executeScript(script);

        // The output will usually come back as a List of Maps
        if (output instanceof List<?>) {
            List<?> nodeList = (List<?>) output;
            for (Object node : nodeList) {
                System.out.println(node);  // prints each node data map
            }
        } else {
            System.out.println("Unexpected output: " + output);
        }
    }


    public static long countTreeOccurrences(GroupHierarchyType type, String nodeText) {
        return getTreePanelTexts().stream()
                .filter(line -> line.contains("Depth " + type.getDepth() + " → " + nodeText))
                .count();
    }

    public static void updateTreeNodeText1(String oldText, String newText) {
        // Escape quotes for safe injection into JS
        String safeOld = oldText.replace("\"", "\\\"");
        String safeNew = newText.replace("\"", "\\\"");

        // Build JS script
        String script = String.format(
                "var tree = Ext.ComponentQuery.query('treepanel')[1];" +
                        "if (tree) {" +
                        "   var root = tree.getStore().getRootNode();" +
                        "   var node = root.findChild('text', \"%s\", true);" +  // true = deep search
                        "   if (node) {" +
                        "       node.set('text', \"%s\");" +
                        "       node.commit();" +
                        "       tree.getView().refresh();" +
                        "   }" +
                        "}",
                safeOld, safeNew
        );

        // Execute in browser
        getJsExecutor().executeScript(script);
    }

    public static void updateTreeNodeText2(String oldText, String newText) {
        String safeOld = oldText.replace("\"", "\\\"");
        String safeNew = newText.replace("\"", "\\\"");

        String script = String.format(
                "try {" +
                        "   var tree = Ext.ComponentQuery.query('treepanel')[1];" +
                        "   var root = tree.getStore().getRootNode();" +
                        "   var node = root.findChild('text', \"%s\", true);" +
                        "   if (!node) throw 'Node not found';" +
                        "   node.set('text', \"%s\");" +
                        "   node.commit();" +
                        "   tree.getView().refresh();" +
                        "   return 'Updated: ' + node.get('text');" +
                        "} catch(e) { return 'ERROR: ' + e; }",
                safeOld, safeNew
        );
        Object result = getJsExecutor().executeScript(script);
        System.out.println("JS Result: " + result);
    }

    public static Map<String, Object> getTreeNodeData(String field, String value) {
        String safeField = field.replace("\"", "\\\"");
        String safeValue = value.replace("\"", "\\\"");

        String script = String.format(
                "try {" +
                        "   var tree = Ext.ComponentQuery.query('treepanel')[1];" +
                        "   if (!tree) throw 'Treepanel not found';" +
                        "   var root = tree.getStore().getRootNode();" +
                        "   var node = root.findChild('%s', \"%s\", true);" +
                        "   if (!node) throw 'Node with %s=%s not found';" +
                        "   return node.getData();" +
                        "} catch(e) { return { error: e.toString() }; }",
                safeField, safeValue, safeField, safeValue
        );

        Object result = getJsExecutor().executeScript(script);

        // Selenium will return a Map<String,Object> when JS returns an object
        @SuppressWarnings("unchecked")
        Map<String, Object> nodeData = (Map<String, Object>) result;

        System.out.println("Node data: " + nodeData);
        return nodeData;
    }

    public static void updateTreeNodeName(String oldName, String newName) {
        String safeOld = oldName.replace("'", "\\'");
        String safeNew = newName.replace("'", "\\'");

        String script = String.format(
                "try {" +
                        "   var tree = Ext.ComponentQuery.query('treepanel')[1];" +
                        "   if (!tree) throw 'Treepanel not found';" +
                        "   var store = tree.getStore();" +
                        "   var node = store.findNode('grpName', '%s');" +
                        "   if (!node) throw 'Node not found for grpName=%s';" +
                        "   node.set('grpName', '%s');" +
                        "   node.set('text', '%s');" +
                        "   node.commit();" +
                        "   return 'Updated: %s';" +
                        "} catch(e) { return 'Error: ' + e; }",
                safeOld, safeOld, safeNew, safeNew, safeNew
        );

        Object result = getJsExecutor().executeScript(script);
        System.out.println("JS Result: " + result);
    }


    public static boolean updateTreeNodeTextUI(String oldText, String newText) {
        String safeOld = oldText.replace("'", "\\'");
        String safeNew = newText.replace("'", "\\'");

        String script = String.format(
                "try {" +
                        "  var updated = false;" +
                        "  Ext.ComponentQuery.query('treepanel').forEach(function(tree) {" +
                        "    var root = tree.getStore().getRootNode();" +
                        "    var target = root.findChild('text', '%s', true);" + // recursive search
                        "    if (target) {" +
                        "      target.set('text', '%s');" +   // updates model + refreshes UI
                        "      updated = true;" +
                        "    }" +
                        "  });" +
                        "  return updated;" +
                        "} catch(e) { return 'Error: ' + e; }",
                safeOld, safeNew
        );

        Object result = getJsExecutor().executeScript(script);
        System.out.println("JS Result: " + result);

        return Boolean.TRUE.equals(result);
    }

    public static void refreshAndExpandTreeNode(String nodeText) {
        String script =
                "Ext.ComponentQuery.query('treepanel').forEach(function(tree) {" +
                        "  var node = tree.getStore().findNode('text', '" + nodeText + "');" +
                        "  if(node) {" +
                        "    node.commit(); " +   // finalize changes
                        "    if(!node.isExpanded()) {" +
                        "       node.expand(); " + // expand to force render
                        "    }" +
                        "    tree.getView().refresh(); " + // force UI redraw
                        "  }" +
                        "});";
        ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script);
    }

    public static void expandNodeAndRefresh(String parentText) {
        String script =
                "Ext.ComponentQuery.query('treepanel').forEach(function(tree) {" +
                        "   var root = tree.getStore().getRootNode();" +
                        "   var node = root.findChild('text', '" + parentText + "', true);" +
                        "   if (node) {" +
                        "       node.expand(false, function() {" +
                        "           tree.getView().refresh(); " + // refresh UI
                        "           console.log('Expanded and refreshed: ' + node.get('text'));" +
                        "       });" +
                        "   } else {" +
                        "       console.log('Parent node not found: " + parentText + "');" +
                        "   }" +
                        "});";

        ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script);
    }

    public static void refreshAndExpandNode(String parentText) {
        String script =
                "Ext.ComponentQuery.query('treepanel').forEach(function(tree) {" +
                        "  var store = tree.getStore();" +
                        "  var node = store.findNode('text', '" + parentText + "');" +
                        "  if(node) {" +
                        "    node.expand(false, function(){ node.reload(); });" +
                        "  }" +
                        "});";
        ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script);
    }

    public static void expandAndReloadNode(String nodeText) {
        String script =
                "Ext.ComponentQuery.query('treepanel').forEach(function(tree) {" +
                        "  var node = tree.getStore().findNode('text', '" + nodeText + "');" +
                        "  if (node) {" +
                        "    if (!node.isExpanded()) {" +
                        "      node.expand(false, function(){ tree.getView().refresh(); });" +
                        "    } else {" +
                        "      tree.getView().refresh();" +
                        "    }" +
                        "  }" +
                        "});";
        ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script);
    }


    public static boolean updateTreeNodeGroupName(String oldText, String newText) {
        String safeOld = oldText.replace("'", "\\'");
        String safeNew = newText.replace("'", "\\'");

        String script = String.format(
                "try {" +
                        "   var tree = Ext.ComponentQuery.query('treepanel')[1];" +
                        "   if (!tree) throw 'Treepanel not found';" +
                        "   var root = tree.getStore().getRootNode();" +
                        "   var node = root.findChild('text', '%s', true);" +
                        "   if (!node) throw 'Node not found for text=%s';" +
                        "   node.set('text', '%s');" +
                        "   node.set('grpName', '%s');" +
                        "   node.commit();" +
                        "   tree.getView().refresh();" +
                        "   return true;" +                  // ✅ return true on success
                        "} catch(e) { return false; }",     // ✅ return false on error
                safeOld, safeOld, safeNew, safeNew
        );

        Object result = ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script);
        return Boolean.TRUE.equals(result);
    }

    public static String updateChildText(String parentGroupName, String childCurrentText, int depth, String newText) {
        String script =
                "try {" +
                        "  var parentGroupName = '" + parentGroupName + "';" +
                        "  var childCurrentText = '" + childCurrentText + "';" +
                        "  var depth = " + depth + ";" +
                        "  var newText = '" + newText + "';" +
                        "  var tree = Ext.ComponentQuery.query('treepanel')[1];" +
                        "  if (tree) {" +
                        "    var root = tree.getStore().getRootNode();" +
                        "    var targetNode = null;" +
                        "    root.cascadeBy(function(node) {" +
                        "      if (!targetNode && node.parentNode && node.parentNode.get('text') === parentGroupName " +
                        "          && node.get('text') === childCurrentText && node.getDepth() === depth) {" +
                        "        targetNode = node;" +
                        "      }" +
                        "    });" +
                        "    if (targetNode) {" +
                        "      var oldText = targetNode.get('text');" +
                        "      targetNode.set('text', newText);" +
                        "      return oldText;" +
                        "    } else {" +
                        "      var parentNode = root.findChild('text', parentGroupName, true);" +
                        "      if (parentNode) {" +
                        "        parentNode.appendChild({ text: newText });" +
                        "      }" +
                        "      return null;" +
                        "    }" +
                        "  } else {" +
                        "    return 'ERROR: Tree not found';" +
                        "  }" +
                        "} catch(e) { return 'ERROR: ' + e.message; }"; // Capture JS errors

        try {
            Object oldText = ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script);
            if (oldText == null) return null;
            String result = oldText.toString();
            if (result.startsWith("ERROR:")) {
                System.err.println(result);  // Log the JS error
                return null;
            }
            return result;
        } catch (JavascriptException je) {
            System.err.println("JavascriptException: " + je.getMessage());
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void focus(By locator) {
        WebElement el = DriverManager.getDriver().findElement(locator);
        ((JavascriptExecutor) DriverManager.getDriver())
                .executeScript("arguments[0].focus();", el);
    }


    public static void printExtJsGridColumns(WebDriver driver, int gridIndex) {

        String script =
                "var grids = Ext.ComponentQuery.query('grid');" +
                        "if (!grids[" + gridIndex + "]) return 'GRID_NOT_FOUND';" +

                        "var cols = grids[" + gridIndex + "].columns;" +
                        "var result = [];" +

                        "Ext.Array.forEach(cols, function(c, idx) {" +
                        "   result.push({" +
                        "       index: idx," +
                        "       text: c.text || ''," +
                        "       dataIndex: c.dataIndex || ''," +
                        "       hidden: !!c.hidden," +
                        "       hasEditor: !!c.getEditor" +
                        "   });" +
                        "});" +

                        "return result;";

        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        Object output = js.executeScript(script);

        if (output instanceof List) {
            List<?> columns = (List<?>) output;
            System.out.println("====== GRID[" + gridIndex + "] COLUMNS ======");
            for (Object col : columns) {
                System.out.println(col);
            }
            System.out.println("=================================");
        } else {
            System.out.println("JS Result: " + output);
        }
    }


    public static void printAllExtJsComboDetails() {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();

        String script =
                "return Ext.ComponentQuery.query('combobox').map(function(c) {" +
                        "    return {" +
                        "        name: c.name || null," +
                        "        fieldLabel: c.fieldLabel || null," +
                        "        itemId: c.itemId || null," +
                        "        id: c.id || null" +
                        "    };" +
                        "});";

        Object result = js.executeScript(script);
        System.out.println("=== Available ExtJS Combos ===");
        System.out.println(result);
    }


    public static void setSubLedgerValue1(String subledgerName) {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();

        String script =
                "var grid = Ext.ComponentQuery.query('gridpanel')[0];" +
                        "if (!grid) throw 'Grid not found';" +

// get leaf columns (real column order)
                        "var cols = grid.getColumnManager().getColumns();" +
                        "var col = null;" +
                        "cols.forEach(function(c){ if (c.text === 'SL Name') col = c; });" +
                        "if (!col) throw 'SL Name column not found';" +

                        "var view = grid.getView();" +
                        "var rowIndex = 0;" +

// correct way to get cell
                        "var cell = view.getCell(rowIndex, col.getVisibleIndex());" +
                        "if (!cell) throw 'SL Name cell not found';" +

                        "cell.dom.click();" +
                        "var editor = grid.findPlugin('cellediting') || grid.plugins[0];" +
                        "if (!editor) throw 'CellEditor plugin not found';" +

                        "setTimeout(function(){ " +
                        "   var field = editor.getEditor(); " +
                        "   if (!field) throw 'Editor field not found';" +

                        "   var store = field.getStore();" +
                        "   store.load({ " +
                        "     callback: function(){ " +
                        "       var rec = store.findRecord(field.displayField, '" + subledgerName + "', 0, false, true, true);" +
                        "       if (!rec) throw 'Value not found: " + subledgerName + "';" +
                        "       field.select(rec);" +
                        "       editor.completeEdit();" +
                        "     }" +
                        "   });" +
                        "}, 300 );";

        js.executeScript(script);

    }

    public static void printExtJsGridColumns() {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();

        String script =
                "var grids = Ext.ComponentQuery.query('gridpanel');" +
                        "if (!grids.length) return console.log('No grids found');" +

                        "var grid = grids[0];" +
                        "var cols = grid.headerCt.getGridColumns(); " +
                        "var names = cols.map(function(c){ return c.text; });" +

                        "console.log('=== GRID COLUMNS ===');" +
                        "console.log(names);" +
                        "return names;";

        Object result = js.executeScript(script);
        System.out.println("GRID COLUMNS: " + result);
    }

    /**
     * Returns a list of ExtJS grid column metadata (text, dataIndex, id).
     */
    /**
     * Returns a list of ExtJS grid column metadata (text, dataIndex, id).
     */
    public static List<Map<String, Object>> getExtJsGridColumns() {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        String script =
                "var grid = Ext.ComponentQuery.query('gridpanel')[0];" +
                        "if (!grid) return 'GRID_NOT_FOUND';" +
                        "var cols = grid.getColumnManager().getColumns();" +
                        "var out = [];" +
                        "cols.forEach(function(c){" +
                        "   out.push({" +
                        "       text: c.text || null," +
                        "       dataIndex: c.dataIndex || null," +
                        "       id: c.id || null" +
                        "   });" +
                        "});" +
                        "return out;";

        Object result = js.executeScript(script);
        System.out.println("Inside getExtJsGridColumns");
        System.out.println("Grid column fetch issue: " + result);
        if (result instanceof List) {
            return (List<Map<String, Object>>) result;
        }


        return Collections.emptyList();
    }

    public static void setSubLedgerValue2(String slName) {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();

        String js1 =
                "var grid = Ext.ComponentQuery.query('gridpanel')[0];" +
                        "if (!grid) throw 'Grid not found';" +

                        // Get visible index safely
                        "var columns = grid.getColumnManager().getColumns();" +
                        "var targetCol = null;" +
                        "columns.forEach(function(c){ if (c.dataIndex === 'subLedgerName') targetCol = c; });" +
                        "if (!targetCol) throw 'SubLedger column not found';" +

                        // Compute actual column index
                        "var colIndex = grid.getColumnManager().getHeaderIndex(targetCol);" +
                        "if (colIndex < 0) throw 'Column is hidden or index invalid';" +

                        // Ensure first row exists
                        "var view = grid.getView();" +
                        "var row = view.getRow(0);" +
                        "if (!row) throw 'Row 0 not rendered yet';" +

                        // Get exact cell by position
                        "var cell = view.getCellByPosition({row:0, column:colIndex});" +
                        "if (!cell) throw 'Cell not found';" +

                        // Click to activate editing
                        "cell.dom.click();" +

                        // Start editing
                        "var plugin = grid.findPlugin('cellediting');" +
                        "if (!plugin) throw 'CellEditing plugin missing';" +

                        "setTimeout(function(){" +
                        "   var editor = plugin.getEditor();" +
                        "   if (!editor) throw 'Editor unavailable';" +

                        "   var field = editor.field;" +
                        "   if (!field) throw 'Combo field missing';" +

                        "   var store = field.getStore();" +
                        "   store.load({" +
                        "     callback: function(){" +
                        "        var rec = store.findRecord(field.displayField, '" + slName + "', 0, false, true, true);" +
                        "        if (!rec) throw 'Sub Ledger not found: " + slName + "';" +
                        "        field.select(rec);" +
                        "        plugin.completeEdit();" +
                        "     }" +
                        "   });" +
                        "}, 300);";

        js.executeScript(js1);
    }

    public static void detectColumnAndCombo() {

        // -------- STEP 1: READ AVAILABLE EXTJS COMBOS ----------
        String comboJson = "[{fieldLabel=Voucher No., id=combo-1219, itemId=cmb_numberschemeList, name=combo-1219-inputEl},"
                + "{fieldLabel=Remarks:, id=combo-1225, itemId=narration, name=narration},"
                + "{fieldLabel=EDITABLE CHOICE LIST, id=combo-1300, itemId=udfstring01, name=combo-1300-inputEl},"
                + "{fieldLabel=PRE DEFINED LIST, id=combo-1301, itemId=udfstring02, name=combo-1301-inputEl}]";

        System.out.println("=== STEP 1 : Available ExtJS Combos ===");

        // Find ONLY the combo you need (Sub Ledger Name or any other target)
        String targetCombo = "EDITABLE CHOICE LIST";

        if (comboJson.contains(targetCombo)) {
            System.out.println("Combo FOUND: " + targetCombo);
        } else {
            System.out.println("Combo NOT FOUND: " + targetCombo);
        }


        // -------- STEP 2: READ GRID COLUMN LIST ----------
        String gridColumns = "[null, Voucher No. <small><span style =\"color:#6F9129;\" >(3)</span></small>, "
                + "Voucher Date <small><span style =\"color:#6F9129;\" >(2)</span></small>, Owner Site, "
                + "Owner Site Short Name, Cash/Bank Name, Reference, Remarks, Receipt Amt (Dr) "
                + "<small><span style =\"color:#6F9129;\" >(1)</span></small>, SLID, SL Name, Release Status, "
                + "Payment Mode, Instrument No., Instrument Date, Cheque Label, Reference, BRS Date, "
                + "Released On, Released By, Created On, Created By, Last Modified On, Last Modified By, "
                + "Year Name, No. of Attachments, Entry Source, PRE DEFINED LIST, TEXT, NUMERIC, DATE FIELD, "
                + "EDITABLE CHOICE LIST, MEMO]";

        System.out.println("\n=== STEP 2 : Checking Grid Columns ===");

        String targetGridColumn = "EDITABLE CHOICE LIST";

        // Split by comma to get index of each grid column
        String[] colArray = gridColumns.split(",");

        int foundIndex = -1;

        for (int i = 0; i < colArray.length; i++) {
            String clean = colArray[i].replaceAll("<.*?>", "").trim(); // remove <small> tags

            if (clean.equalsIgnoreCase(targetGridColumn)) {
                foundIndex = i;
                System.out.println("Grid Column FOUND at index " + i + ": " + clean);
                break;
            }
        }

        if (foundIndex == -1) {
            System.out.println("Grid Column NOT FOUND: " + targetGridColumn);
        }
    }

    public static void setSubLedgerName(WebDriver driver, int rowIndex, String subLedgerName) {
        System.out.println("=== STEP 1 : START Sub Ledger Setter ===");

        String script =
                "try {"
                        + "console.log('--- SubLedger SETTER START ---');"

// 1. Get grid
                        + "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) throw 'Grid not found';"

// 2. Find SL Name column
                        + "var cols = grid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "cols.forEach(function(c,idx){ if (c.dataIndex === 'subLedgerName') slCol = c; });"
                        + "if (!slCol) throw 'SL Name column NOT FOUND';"

// 3. Force row into view to render DOM
                        + "var view = grid.getView();"
                        + "view.scrollRowIntoView(0, false);"

// 4. REFETCH rendered row (important!)
                        + "var row = view.getRow(0);"
                        + "if (!row) throw 'Row not rendered EVEN after scroll';"

// 5. Now fetch cell safely
                        + "var cell = Ext.fly(row).down('td[data-columnid=' + slCol.id + ']');"
                        + "if (!cell) throw 'SL Name cell NOT FOUND';"

// 6. Click the cell safely
                        + "cell.dom.click();"

// 7. Use editor plugin
                        + "var editor = grid.findPlugin('cellediting') || grid.plugins[0];"
                        + "if (!editor) throw 'CellEditing plugin not found';"

// 8. Load dropdown and select value
                        + "setTimeout(function(){"
                        + "   var field = editor.getEditor();"
                        + "   if (!field) throw 'Editor field not found';"
                        + "   var store = field.getStore();"
                        + "   store.load({"
                        + "       callback:function(){"
                        + "           var rec = store.findRecord(field.displayField, 'ABUL KASHEM MOLLHA', 0, false, true, true);"
                        + "           if (!rec) throw 'SubLedger not found';"
                        + "           field.select(rec);"
                        + "           editor.completeEdit();"
                        + "       }"
                        + "   });"
                        + "}, 400);"

                        + "} catch(e){ console.error(e); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);

        System.out.println("=== STEP 2 : Sub Ledger '" + subLedgerName + "' SET for row " + rowIndex + " ===");
    }


    public static void printGridColumns(WebDriver driver) {

        System.out.println("=== STEP 1 : START Column Debugging ===");

        String script =
                "try {"
                        + "console.log('--- COLUMN DEBUG START ---');"

                        // 1. Get grid
                        + "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) { console.error('Grid NOT FOUND'); return; }"

                        // 2. Get columns
                        + "var cols = grid.getColumnManager().getColumns();"

                        // 3. Print column information
                        + "cols.forEach(function(c, idx){"
                        + "    console.log('COLUMN #'+idx,"
                        + "        'TEXT:', c.text,"
                        + "        'DATAINDEX:', c.dataIndex,"
                        + "        'ID:', c.id,"
                        + "        'EDITABLE:', c.editable,"
                        + "        'HIDDEN:', c.hidden,"
                        + "        'EDITOR:', (c.getEditor ? c.getEditor() : 'NO EDITOR')"
                        + "    );"
                        + "});"

                        + "console.log('--- COLUMN DEBUG END ---');"

                        + "} catch(e){ console.error('ERROR:', e); }";

        ((JavascriptExecutor) driver).executeScript(script);

        System.out.println("=== STEP 2 : Column Debug Script Executed ===");
    }

    public static void printEditableColumns(WebDriver driver) {

        System.out.println("=== STEP 1 : START Editable Column Debug ===");

        String script =
                "try {"
                        + "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) { console.error('Grid NOT FOUND'); return; }"

                        + "var cols = grid.getColumnManager().getColumns();"

                        + "cols.forEach(function(c, idx){"
                        + "   var editor = (c.getEditor ? c.getEditor() : null);"
                        + "   if (editor) {"
                        + "       console.log('EDITABLE COLUMN FOUND:', {"
                        + "           index: idx,"
                        + "           text: c.text,"
                        + "           dataIndex: c.dataIndex,"
                        + "           id: c.id,"
                        + "           hidden: c.hidden,"
                        + "           editorType: editor.xtype"
                        + "       });"
                        + "   }"
                        + "});"
                        + "} catch(e){ console.error('Error:', e); }";

        ((JavascriptExecutor) driver).executeScript(script);

        System.out.println("=== STEP 2 : Editable Column Debug Completed ===");
    }

    public static void setSubLedgeroldrefsitenotadded(WebDriver driver, int rowIndex, String slName) {

        System.out.println("=== STEP 1 : START Set Sub Ledger for Row " + rowIndex + " ===");

        String script =
                "try {"
                        + "console.log('--- START SET SL ---');"

                        // Get grid
                        + "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) throw 'Grid not found';"

                        // Get SL column
                        + "var cols = grid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "cols.forEach(function(c){ if (c.dataIndex === 'subLedgerName') slCol = c; });"
                        + "if (!slCol) throw 'SL column not found';"

                        // Click cell to open editor
                        + "var view = grid.getView();"
                        + "var row = view.getRow(" + rowIndex + ");"
                        + "if (!row) throw 'Row not found';"

                        + "var cell = Ext.fly(row).down('td[data-columnid=' + slCol.id + ']');"
                        + "if (!cell) throw 'SL cell not found';"
                        + "cell.dom.click();"

                        // Delay so combo appears
                        + "setTimeout(function(){"

                        // NOW get the REAL SL dropdown (slCode)
                        + "var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "if (!combo) { console.error('slCode combo NOT found'); return; }"

                        + "combo.getStore().load({"
                        + "   callback: function(){"
                        + "       var rec = combo.getStore().findRecord(combo.displayField, '" + slName + "');"
                        + "       if (!rec) { console.error('SL not found'); return; }"
                        + "       combo.select(rec);"
                        + "       combo.fireEvent('select', combo, rec);"
                        + "       combo.blur();"
                        + "   }"
                        + "});"

                        + "}, 300);"

                        + "} catch(e){ console.error(e); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);

        System.out.println("=== STEP 2 : SL '" + slName + "' SET SUCCESSFULLY ===");
    }

    public static void setSubLedger3(WebDriver driver, int rowIndex, String slName) {
        System.out.println("=== STEP 1 : START Set Sub Ledger for Row " + rowIndex + " ===");

        String script =
                "try {"
                        + "console.log('--- START SET SL ---');"
                        + "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) throw 'Grid not found';"
                        + "var cols = grid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "cols.forEach(function(c){ if (c.dataIndex === 'subLedgerName') slCol = c; });"
                        + "if (!slCol) throw 'SL column not found';"
                        + "var view = grid.getView();"
                        + "var row = view.getRow(" + rowIndex + ");"
                        + "if (!row) throw 'Row not found';"
                        + "var cell = Ext.fly(row).down('td[data-columnid=' + slCol.id + ']');"
                        + "if (!cell) throw 'SL cell not found';"
                        + "cell.dom.click();"

                        + "setTimeout(function(){"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) { console.error('slCode combo NOT found'); return; }"

                        + "  combo.getStore().load({"
                        + "    callback: function(){"
                        + "      var rec = combo.getStore().findRecord(combo.displayField, '" + slName + "');"
                        + "      if (!rec) { console.error('SL not found: " + slName + "'); return; }"

                        // Set value and fire select
                        + "      combo.select(rec);"
                        + "      combo.fireEvent('select', combo, [rec]);"  // Note: some ExtJS versions expect array

                        // After select, look for dependent combos and trigger their load
                        + "      setTimeout(function(){"

                        // Force fire change event too (triggers dependent field population)
                        + "        combo.fireEvent('change', combo, rec.get(combo.valueField), null);"

                        // Find and trigger Sub Ledger Id field if it has a store dependency
                        + "        var slIdCombo = Ext.ComponentQuery.query('combo[name=slId]')[0]"
                        + "                     || Ext.ComponentQuery.query('combo[name=subLedgerId]')[0];"
                        + "        if (slIdCombo && slIdCombo.getStore()) {"
                        + "          slIdCombo.getStore().load({ callback: function(){"
                        + "            console.log('slId store loaded, count: ' + slIdCombo.getStore().getCount());"
                        + "          }});"
                        + "        }"

                        // Find Reference Site combo and reload
                        + "        var siteCombo = Ext.ComponentQuery.query('combo[name=referenceSite]')[0]"
                        + "                      || Ext.ComponentQuery.query('combo[name=site]')[0];"
                        + "        if (siteCombo && siteCombo.getStore()) {"
                        + "          siteCombo.getStore().load({ callback: function(){"
                        + "            console.log('site store loaded, count: ' + siteCombo.getStore().getCount());"
                        + "          }});"
                        + "        }"

                        + "        combo.blur();"
                        + "      }, 500);"
                        + "    }"
                        + "  });"
                        + "}, 300);"
                        + "} catch(e){ console.error(e); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== STEP 2 : SL '" + slName + "' SET SUCCESSFULLY ===");
    }

    public static void diagnoseRowEditorFields(WebDriver driver, int rowIndex) {
        System.out.println("=== DIAGNOSING ROW EDITOR FIELDS FOR ROW INDEX: " + rowIndex + " ===");

        // Step 1: Click the cell to open the row editor
        String clickScript =
                "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) { console.error('Grid not found'); return; }"
                        + "var view = grid.getView();"
                        + "var row = view.getRow(" + rowIndex + ");"
                        + "if (!row) { console.error('Row not found'); return; }"
                        + "var cell = Ext.fly(row).query('td')[1];"
                        + "if (!cell) { console.error('Cell not found'); return; }"
                        + "cell.click();"
                        + "console.log('Cell clicked for row " + rowIndex + "');";

        ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("--- Cell clicked for row: " + rowIndex + " ---");

        // Step 2: Wait for editor to open
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            System.out.println("Sleep interrupted: " + e.getMessage());
        }

        // Step 3: Extract combo and field details
        String diagnoseScript =
                "var result = [];"

                        // Scan all combos
                        + "var allCombos = Ext.ComponentQuery.query('combo');"
                        + "result.push('=== COMBOS FOUND: ' + allCombos.length + ' ===');"
                        + "allCombos.forEach(function(c, i){"
                        + "  result.push("
                        + "    'COMBO[' + i + '] '"
                        + "    + '| name='          + (c.name         || 'N/A') "
                        + "    + '| fieldLabel='    + (c.fieldLabel    || 'N/A') "
                        + "    + '| xtype='         + (c.xtype         || 'N/A') "
                        + "    + '| displayField='  + (c.displayField  || 'N/A') "
                        + "    + '| valueField='    + (c.valueField    || 'N/A') "
                        + "    + '| currentValue='  + (c.getValue()    || 'N/A') "
                        + "    + '| storeCount='    + (c.getStore() ? c.getStore().getCount() : 'NO STORE')"
                        + "  );"
                        + "});"

                        // Scan all text/number/date fields
                        + "var allFields = Ext.ComponentQuery.query('field');"
                        + "result.push('=== ALL FIELDS FOUND: ' + allFields.length + ' ===');"
                        + "allFields.forEach(function(f, i){"
                        + "  result.push("
                        + "    'FIELD[' + i + '] '"
                        + "    + '| name='        + (f.name       || 'N/A') "
                        + "    + '| fieldLabel='  + (f.fieldLabel  || 'N/A') "
                        + "    + '| xtype='       + (f.xtype       || 'N/A') "
                        + "    + '| value='       + (f.getValue()  !== undefined ? f.getValue() : 'N/A')"
                        + "  );"
                        + "});"

                        // Scan row editor plugin specifically
                        + "var rowEditors = Ext.ComponentQuery.query('roweditor');"
                        + "result.push('=== ROW EDITORS FOUND: ' + rowEditors.length + ' ===');"
                        + "rowEditors.forEach(function(re, i){"
                        + "  result.push('ROW_EDITOR[' + i + '] isVisible=' + re.isVisible());"
                        + "  if (re.items && re.items.items) {"
                        + "    re.items.items.forEach(function(item, j){"
                        + "      result.push("
                        + "        '  EDITOR_ITEM[' + j + '] '"
                        + "        + '| name='       + (item.name       || 'N/A') "
                        + "        + '| fieldLabel=' + (item.fieldLabel  || 'N/A') "
                        + "        + '| xtype='      + (item.xtype       || 'N/A') "
                        + "        + '| value='      + (item.getValue ? item.getValue() : 'N/A')"
                        + "      );"
                        + "    });"
                        + "  }"
                        + "});"

                        + "return result.join('\\n');";

        Object rawResult = ((JavascriptExecutor) driver).executeScript(diagnoseScript);

        System.out.println("=== RAW FIELD DIAGNOSIS OUTPUT START ===");
        if (rawResult != null) {
            String[] lines = rawResult.toString().split("\n");
            for (String line : lines) {
                System.out.println("[FIELD_DIAG] " + line);
            }
        } else {
            System.out.println("[FIELD_DIAG] No output returned. Editor may not have opened.");
        }
        System.out.println("=== RAW FIELD DIAGNOSIS OUTPUT END ===");
        System.out.println("=== DIAGNOSIS COMPLETE FOR ROW INDEX: " + rowIndex + " ===");
    }

    public static void setSubLedger4(WebDriver driver, int rowIndex, String slName) {
        System.out.println("=== STEP 1 : START Set Sub Ledger for Row " + rowIndex + " ===");

        String script =
                "try {"
                        + "console.log('--- START SET SL ---');"

                        // Get grid
                        + "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) throw 'Grid not found';"

                        // Get SL column (subLedgerName)
                        + "var cols = grid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "cols.forEach(function(c){ if (c.dataIndex === 'subLedgerName') slCol = c; });"
                        + "if (!slCol) throw 'SL column not found';"

                        // Click cell to trigger editor
                        + "var view = grid.getView();"
                        + "var row = view.getRow(" + rowIndex + ");"
                        + "if (!row) throw 'Row not found';"
                        + "var cell = Ext.fly(row).down('td[data-columnid=' + slCol.id + ']');"
                        + "if (!cell) throw 'SL cell not found';"
                        + "cell.dom.click();"

                        + "setTimeout(function(){"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) { console.error('slCode combo NOT found'); return; }"

                        + "  combo.getStore().load({"
                        + "    callback: function(){"
                        + "      var store = combo.getStore();"
                        + "      var rec = store.findRecord('slName', '" + slName + "');"
                        + "      if (!rec) { console.error('SL record not found for: " + slName + "'); return; }"

                        // Log all fields in the found record for debugging
                        + "      console.log('SL Record Data: ' + JSON.stringify(rec.data));"

                        // Select in combo
                        + "      combo.select(rec);"
                        + "      combo.fireEvent('select', combo, rec);"

                        // Get the grid row record and manually set all 3 missing fields
                        + "      setTimeout(function(){"
                        + "        var gridStore = grid.getStore();"
                        + "        var gridRec = gridStore.getAt(" + rowIndex + ");"
                        + "        if (!gridRec) { console.error('Grid record not found'); return; }"

                        // Set SLID (dataIndex=slid)
                        + "        var slId = rec.get('slCode') || rec.get('slId') || rec.get('id') || '';"
                        + "        gridRec.set('slid', slId);"
                        + "        console.log('SET slid: ' + slId);"

                        // Set Sub Ledger Name
                        + "        gridRec.set('subLedgerName', '" + slName + "');"
                        + "        console.log('SET subLedgerName: " + slName + "');"

                        // Set Sub Ledger Class Name (check possible field names in rec.data)
                        + "        var slClass = rec.get('slClassName') || rec.get('subLedgerClass')"
                        + "                    || rec.get('slClass') || rec.get('jobberName') || '';"
                        + "        gridRec.set('subLedgerClass', slClass);"  // adjust dataIndex if needed
                        + "        console.log('SET subLedgerClass: ' + slClass);"

                        // Set Reference Site (check possible field names in rec.data)
                        + "        var refSite = rec.get('referenceSite') || rec.get('siteName')"
                        + "                    || rec.get('ownerSite') || rec.get('site') || '';"
                        + "        gridRec.set('referenceSite', refSite);"  // adjust dataIndex if needed
                        + "        console.log('SET referenceSite: ' + refSite);"

                        // Refresh grid view to show updated values
                        + "        view.refreshNode(" + rowIndex + ");"
                        + "        combo.blur();"
                        + "        console.log('--- ALL FIELDS SET SUCCESSFULLY ---');"
                        + "      }, 500);"
                        + "    }"
                        + "  });"
                        + "}, 300);"
                        + "} catch(e){ console.error('ERROR: ' + e); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== STEP 2 : SL '" + slName + "' SET SUCCESSFULLY ===");

        // Wait for all async operations to complete
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            System.out.println("Sleep interrupted: " + e.getMessage());
        }

        // Step 3: Log the actual record data to verify what was set
        verifyRowData(driver, rowIndex);
    }

    /**
     * Verifies and prints the grid row data after setting SL
     */
    public static void verifyRowData(WebDriver driver, int rowIndex) {
        System.out.println("=== VERIFYING ROW DATA FOR INDEX: " + rowIndex + " ===");

        String verifyScript =
                "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "var rec = grid.getStore().getAt(" + rowIndex + ");"
                        + "if (!rec) return 'Record not found';"
                        + "var result = [];"
                        + "result.push('subLedgerName: '  + rec.get('subLedgerName'));"
                        + "result.push('slid: '           + rec.get('slid'));"
                        + "result.push('subLedgerClass: ' + rec.get('subLedgerClass'));"
                        + "result.push('referenceSite: '  + rec.get('referenceSite'));"
                        // Also dump full record to catch correct field names
                        + "result.push('FULL_DATA: '      + JSON.stringify(rec.data));"
                        + "return result.join('\\n');";

        Object result = ((JavascriptExecutor) driver).executeScript(verifyScript);
        if (result != null) {
            String[] lines = result.toString().split("\n");
            for (String line : lines) {
                System.out.println("[VERIFY] " + line);
            }
        }
        System.out.println("=== VERIFICATION COMPLETE ===");
    }

    public static void captureSlApiCall(WebDriver driver, int rowIndex) {
        System.out.println("=== CAPTURING SL API CALL ===");

        // Intercept XHR before clicking
        String interceptScript =
                "window._capturedRequests = [];"
                        + "var origOpen = XMLHttpRequest.prototype.open;"
                        + "var origSend = XMLHttpRequest.prototype.send;"
                        + "XMLHttpRequest.prototype.open = function(method, url) {"
                        + "  this._url = url;"
                        + "  this._method = method;"
                        + "  origOpen.apply(this, arguments);"
                        + "};"
                        + "XMLHttpRequest.prototype.send = function(body) {"
                        + "  var self = this;"
                        + "  this.addEventListener('load', function() {"
                        + "    window._capturedRequests.push({"
                        + "      url: self._url,"
                        + "      method: self._method,"
                        + "      requestBody: body,"
                        + "      responseText: self.responseText.substring(0, 500)"
                        + "    });"
                        + "  });"
                        + "  origSend.apply(this, arguments);"
                        + "};";

        ((JavascriptExecutor) driver).executeScript(interceptScript);
        System.out.println("--- XHR Interceptor Installed ---");

        // Now click the SL cell to trigger the editor
        String clickScript =
                "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "var cols = grid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "cols.forEach(function(c){ if (c.dataIndex === 'subLedgerName') slCol = c; });"
                        + "var view = grid.getView();"
                        + "var row = view.getRow(" + rowIndex + ");"
                        + "var cell = Ext.fly(row).down('td[data-columnid=' + slCol.id + ']');"
                        + "cell.dom.click();";

        ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("--- SL Cell Clicked ---");

        // Wait for combo to appear, then select
        try { Thread.sleep(800); } catch (InterruptedException e) { e.printStackTrace(); }

        String selectScript =
                "var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "combo.getStore().load({"
                        + "  callback: function(){"
                        + "    var rec = combo.getStore().findRecord('slName', 'ABUL KASHEM MOLLHA');"
                        + "    if(rec) {"
                        + "      combo.select(rec);"
                        + "      combo.fireEvent('select', combo, rec);"
                        + "    }"
                        + "  }"
                        + "});";

        ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("--- SL Selected, Waiting for API calls ---");

        // Wait for any triggered API calls
        try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }

        // Dump all captured requests
        String dumpScript =
                "var result = [];"
                        + "window._capturedRequests.forEach(function(r, i){"
                        + "  result.push('REQUEST[' + i + '] '"
                        + "    + '| method=' + r.method"
                        + "    + '| url=' + r.url"
                        + "    + '| body=' + (r.requestBody || 'null')"
                        + "    + '| response=' + r.responseText"
                        + "  );"
                        + "});"
                        + "return result.length > 0 ? result.join('\\n') : 'NO REQUESTS CAPTURED';";

        Object result = ((JavascriptExecutor) driver).executeScript(dumpScript);
        System.out.println("=== CAPTURED API CALLS ===");
        if (result != null) {
            String[] lines = result.toString().split("\n");
            for (String line : lines) {
                System.out.println("[API] " + line);
            }
        }
        System.out.println("=== CAPTURE COMPLETE ===");
    }

    public static void setSubLedger5(WebDriver driver, int rowIndex, String slName) {
        System.out.println("=== STEP 1 : START Set Sub Ledger for Row " + rowIndex + " ===");

        String script =
                "try {"
                        + "console.log('--- START SET SL ---');"

                        + "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) throw 'Grid not found';"

                        + "var cols = grid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "cols.forEach(function(c){ if (c.dataIndex === 'subLedgerName') slCol = c; });"
                        + "if (!slCol) throw 'SL column not found';"

                        + "var view = grid.getView();"
                        + "var row = view.getRow(" + rowIndex + ");"
                        + "if (!row) throw 'Row not found';"

                        + "var cell = Ext.fly(row).down('td[data-columnid=' + slCol.id + ']');"
                        + "if (!cell) throw 'SL cell not found';"
                        + "cell.dom.click();"

                        + "setTimeout(function(){"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) { console.error('slCode combo NOT found'); return; }"

                        // Load with exact slName filter to get the right record
                        + "  var store = combo.getStore();"
                        + "  store.getProxy().setExtraParam('queryFieldValue', '" + slName + "');"
                        + "  store.load({"
                        + "    callback: function(){"
                        + "      console.log('Store loaded, count: ' + store.getCount());"

                        // Find the record
                        + "      var rec = null;"
                        + "      store.each(function(r){"
                        + "        if (r.get('slName') === '" + slName + "') { rec = r; return false; }"
                        + "      });"
                        + "      if (!rec) {"
                        + "        console.error('SL record not found for: " + slName + "');"
                        + "        return;"
                        + "      }"

                        // Log full record to confirm field names
                        + "      console.log('SL REC DATA: ' + JSON.stringify(rec.data));"

                        // Extract the 3 values from API response fields
                        + "      var slIdVal    = rec.get('slid')      || '';"   // Sub Ledger Id  e.g. "J061"
                        + "      var slClass    = rec.get('className') || '';"   // Sub Ledger Class e.g. "Jobber"
                        + "      var glName     = rec.get('glName')    || '';"   // General Ledger Name
                        + "      var slAbbre    = rec.get('slAbbre')   || '';"

                        + "      console.log('slid=' + slIdVal + ' | className=' + slClass);"

                        // Select in combo
                        + "      combo.select(rec);"
                        + "      combo.fireEvent('select', combo, rec);"

                        // Update grid record with all 3 missing fields
                        + "      setTimeout(function(){"
                        + "        var gridRec = grid.getStore().getAt(" + rowIndex + ");"
                        + "        if (!gridRec) { console.error('Grid record not found at index " + rowIndex + "'); return; }"

                        // 1. Sub Ledger Id (shown in SLID column, dataIndex=slid)
                        + "        gridRec.set('slid', slIdVal);"
                        + "        console.log('SET slid => ' + slIdVal);"

                        // 2. Sub Ledger Class Name (dataIndex=subLedgerClass)
                        + "        gridRec.set('subLedgerClass', slClass);"
                        + "        console.log('SET subLedgerClass => ' + slClass);"

                        // 3. Reference Site — search for site-specific field in record
                        //    The API response has slSiteCount; reference site may need separate lookup
                        //    Try getting from combo's linked site field or txtLedgerBalance context
                        + "        var siteField = Ext.ComponentQuery.query('field[name=txtLedgerBalance]')[0];"
                        + "        var refSiteVal = siteField ? siteField.getValue() : '';"

                        // Also try firing the select event which may trigger site population internally
                        + "        combo.fireEvent('change', combo, rec.get(combo.valueField));"

                        + "        setTimeout(function(){"
                        // Re-read referenceSite after change event fires
                        + "          var updatedRec = grid.getStore().getAt(" + rowIndex + ");"
                        + "          console.log('POST-CHANGE referenceSite: ' + updatedRec.get('referenceSite'));"
                        + "          console.log('POST-CHANGE subLedgerClass: ' + updatedRec.get('subLedgerClass'));"
                        + "          view.refreshNode(" + rowIndex + ");"
                        + "        }, 1000);"

                        + "        combo.blur();"
                        + "      }, 500);"
                        + "    }"
                        + "  });"
                        + "}, 300);"
                        + "} catch(e){ console.error('ERROR: ' + e); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== STEP 2 : JS Executed for SL '" + slName + "' ===");

        try { Thread.sleep(2500); } catch (InterruptedException e) { e.printStackTrace(); }

        // Verify and print all field values
        verifyAndPrintSlRecord(driver, rowIndex);
    }

    public static void verifyAndPrintSlRecord1(WebDriver driver, int rowIndex) {
        System.out.println("=== VERIFYING SL RECORD FOR ROW: " + rowIndex + " ===");

        String script =
                "var grid  = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "var rec  = grid.getStore().getAt(" + rowIndex + ");"
                        + "if (!rec) return 'NO RECORD FOUND';"
                        + "var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "var storeRec = combo ? combo.getStore().findRecord('slCode', rec.get('slid')) : null;"
                        + "var result = [];"
                        + "result.push('--- GRID RECORD ---');"
                        + "result.push('subLedgerName  : ' + rec.get('subLedgerName'));"
                        + "result.push('slid           : ' + rec.get('slid'));"
                        + "result.push('subLedgerClass : ' + rec.get('subLedgerClass'));"
                        + "result.push('referenceSite  : ' + rec.get('referenceSite'));"
                        + "if (storeRec) {"
                        + "  result.push('--- STORE RECORD (slCode combo) ---');"
                        + "  result.push('slid (slid)     : ' + storeRec.get('slid'));"
                        + "  result.push('className       : ' + storeRec.get('className'));"
                        + "  result.push('slAbbre         : ' + storeRec.get('slAbbre'));"
                        + "  result.push('FULL STORE REC  : ' + JSON.stringify(storeRec.data));"
                        + "}"
                        + "return result.join('\\n');";

        Object result = ((JavascriptExecutor) driver).executeScript(script);
        if (result != null) {
            String[] lines = result.toString().split("\n");
            for (String line : lines) {
                System.out.println("[VERIFY] " + line);
            }
        }
        System.out.println("=== VERIFICATION COMPLETE ===");
    }


    public static void setSubLedger6(WebDriver driver, int rowIndex, String slName) {
        System.out.println("=== STEP 1 : START Set Sub Ledger for Row " + rowIndex + " ===");

        String script =
                "try {"
                        + "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!grid) throw 'Grid not found';"

                        + "var cols = grid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "cols.forEach(function(c){ if (c.dataIndex === 'subLedgerName') slCol = c; });"
                        + "if (!slCol) throw 'SL column not found';"

                        + "var view = grid.getView();"
                        + "var row = view.getRow(" + rowIndex + ");"
                        + "if (!row) throw 'Row not found';"

                        + "var cell = Ext.fly(row).down('td[data-columnid=' + slCol.id + ']');"
                        + "if (!cell) throw 'SL cell not found';"
                        + "cell.dom.click();"

                        + "setTimeout(function(){"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) { console.error('slCode combo NOT found'); return; }"

                        + "  combo.getStore().load({"
                        + "    callback: function(){"
                        + "      var rec = null;"
                        + "      combo.getStore().each(function(r){"
                        + "        if (r.get('slName') === '" + slName + "') { rec = r; return false; }"
                        + "      });"
                        + "      if (!rec) { console.error('SL not found: " + slName + "'); return; }"

                        + "      console.log('SL REC: ' + JSON.stringify(rec.data));"

                        // Select in combo
                        + "      combo.select(rec);"
                        + "      combo.fireEvent('select', combo, rec);"

                        + "      setTimeout(function(){"
                        + "        var gridRec = grid.getStore().getAt(" + rowIndex + ");"
                        + "        if (!gridRec) { console.error('Grid record not found'); return; }"

                        // Set values on grid model record
                        + "        gridRec.set('slid',          rec.get('slid')      || '');"
                        + "        gridRec.set('subLedgerClass', rec.get('className') || '');"
                        + "        gridRec.set('subLedgerName',  rec.get('slName')    || '');"

                        + "        console.log('SET slid='          + gridRec.get('slid'));"
                        + "        console.log('SET subLedgerClass=' + gridRec.get('subLedgerClass'));"
                        + "        console.log('SET subLedgerName='  + gridRec.get('subLedgerName'));"

                        // KEY FIX: Complete the edit via cellediting plugin so UI re-renders the row
                        + "        var plugin = null;"
                        + "        grid.plugins.forEach(function(p){"
                        + "          if (p.ptype === 'cellediting' || p.isXType && p.isXType('cellediting')) plugin = p;"
                        + "        });"
                        + "        if (plugin) {"
                        + "          console.log('CellEditing plugin found, completing edit...');"
                        + "          plugin.completeEdit();"
                        + "        } else {"
                        // Fallback: blur the combo and refresh the row
                        + "          console.warn('No cellediting plugin found, using fallback refresh');"
                        + "          combo.blur();"
                        + "          view.refreshNode(" + rowIndex + ");"
                        + "        }"

                        // Final refresh to ensure UI shows updated values
                        + "        setTimeout(function(){"
                        + "          view.refreshNode(" + rowIndex + ");"
                        + "          console.log('--- ROW REFRESHED ---');"
                        + "        }, 300);"

                        + "      }, 500);"
                        + "    }"
                        + "  });"
                        + "}, 300);"
                        + "} catch(e){ console.error('ERROR: ' + e); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== STEP 2 : JS Executed for SL '" + slName + "' ===");

        try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }

        verifyAndPrintSlRecord(driver, rowIndex);
    }

    public static void verifyAndPrintSlRecord(WebDriver driver, int rowIndex) {
        System.out.println("=== VERIFYING SL RECORD FOR ROW: " + rowIndex + " ===");

        String script =
                "var grid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "var rec  = grid.getStore().getAt(" + rowIndex + ");"
                        + "if (!rec) return 'NO RECORD FOUND';"
                        + "var result = [];"
                        + "result.push('subLedgerName  : ' + rec.get('subLedgerName'));"
                        + "result.push('slid           : ' + rec.get('slid'));"
                        + "result.push('subLedgerClass : ' + rec.get('subLedgerClass'));"
                        + "result.push('referenceSite  : ' + rec.get('referenceSite'));"

                        // Also check what plugins the grid has
                        + "var pluginNames = [];"
                        + "grid.plugins.forEach(function(p){ pluginNames.push(p.ptype || p.alias || 'unknown'); });"
                        + "result.push('grid plugins   : ' + pluginNames.join(', '));"

                        // Check what the UI actually shows in the cells
                        + "var view = grid.getView();"
                        + "var row  = view.getRow(" + rowIndex + ");"
                        + "if (row) {"
                        + "  var cells = Ext.fly(row).query('td');"
                        + "  result.push('UI CELL COUNT  : ' + cells.length);"
                        + "  cells.forEach(function(cell, i){"
                        + "    var txt = cell.innerText || cell.textContent || '';"
                        + "    if (txt.trim()) result.push('CELL[' + i + '] = ' + txt.trim());"
                        + "  });"
                        + "}"
                        + "return result.join('\\n');";

        Object result = ((JavascriptExecutor) driver).executeScript(script);
        if (result != null) {
            String[] lines = result.toString().split("\n");
            for (String line : lines) {
                System.out.println("[VERIFY] " + line);
            }
        }
        System.out.println("=== VERIFICATION COMPLETE ===");
    }


    public static void diagnoseAllGrids(WebDriver driver) {
        System.out.println("=== DIAGNOSING ALL GRIDS ===");

        String script =
                "var result = [];"
                        + "try {"
                        + "  var allGrids = Ext.ComponentQuery.query('gridpanel');"
                        + "  result.push('TOTAL GRIDS: ' + allGrids.length);"
                        + "  for (var i = 0; i < allGrids.length; i++) {"
                        + "    try {"
                        + "      var g = allGrids[i];"
                        + "      result.push('=== GRID[' + i + ']'"
                        + "        + ' id='      + g.id"
                        + "        + ' itemId='  + (g.itemId  || 'N/A')"
                        + "        + ' title='   + (g.title   || 'N/A')"
                        + "        + ' visible=' + g.isVisible()"
                        + "        + ' records=' + g.getStore().getCount());"

                        // Null-safe columns — NO .replace() at all, use innerText trick
                        + "      var gcols = g.getColumnManager().getColumns();"
                        + "      for (var j = 0; j < gcols.length; j++) {"
                        + "        var c = gcols[j];"
                        + "        var colText = '';"
                        + "        try {"
                        + "          if (c.text !== null && c.text !== undefined) {"
                        + "            var tmp = document.createElement('div');"
                        + "            tmp.innerHTML = String(c.text);"
                        + "            colText = tmp.innerText || tmp.textContent || String(c.text);"
                        + "          } else { colText = 'NULL'; }"
                        + "        } catch(ce) { colText = 'ERR:' + ce.message; }"
                        + "        result.push('  COL[' + j + '] dataIndex=' + (c.dataIndex || 'null')"
                        + "          + ' text=' + colText.trim());"
                        + "      }"

                        // Store records max 3
                        + "      var recs = g.getStore().getRange(0, 2);"
                        + "      for (var k = 0; k < recs.length; k++) {"
                        + "        try {"
                        + "          result.push('  STORE_REC[' + k + ']: ' + JSON.stringify(recs[k].data));"
                        + "        } catch(re) { result.push('  STORE_REC[' + k + ']: ERR ' + re.message); }"
                        + "      }"
                        + "    } catch(ge) {"
                        + "      result.push('  GRID[' + i + '] ERROR: ' + ge.message);"
                        + "    }"
                        + "  }"
                        + "} catch(e) {"
                        + "  result.push('TOP LEVEL ERROR: ' + e.message);"
                        + "}"
                        + "return result.join('\\n');";

        Object result = ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== GRID DIAGNOSIS OUTPUT ===");
        if (result != null) {
            String[] lines = result.toString().split("\n");
            for (String line : lines) {
                System.out.println("[GRID_DIAG] " + line);
            }
        } else {
            System.out.println("[GRID_DIAG] No output returned.");
        }
        System.out.println("=== DIAGNOSIS COMPLETE ===");
    }


    public static void setSubLedger7(WebDriver driver, int rowIndex, String slName) {
        System.out.println("=== STEP 1 : START Set Sub Ledger for Row " + rowIndex + " ===");

        String script =
                "try {"
                        + "var mainGrid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "if (!mainGrid) throw 'Main grid not found';"

                        // Get child grid by itemId
                        + "var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "if (!childGrid) throw 'Child grid arvch_grid not found';"
                        + "console.log('Child grid found: ' + childGrid.id);"

                        // Click SL cell in child grid row to open editor
                        + "var cols = childGrid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "for (var i = 0; i < cols.length; i++) {"
                        + "  if (cols[i].dataIndex === 'slCode') { slCol = cols[i]; break; }"
                        + "}"
                        + "if (!slCol) throw 'slCode column not found in child grid';"

                        + "var view = childGrid.getView();"
                        + "var row = view.getRow(" + rowIndex + ");"
                        + "if (!row) throw 'Child grid row not found at index " + rowIndex + "';"

                        + "var cell = Ext.fly(row).down('td[data-columnid=' + slCol.id + ']');"
                        + "if (!cell) throw 'slCode cell not found';"
                        + "cell.dom.click();"
                        + "console.log('Child grid slCode cell clicked');"

                        + "setTimeout(function(){"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) { console.error('slCode combo not found'); return; }"

                        + "  combo.getStore().load({"
                        + "    callback: function(){"
                        + "      var store = combo.getStore();"
                        + "      console.log('SL store loaded, count: ' + store.getCount());"

                        // Find the matching SL record
                        + "      var rec = null;"
                        + "      store.each(function(r){"
                        + "        if (r.get('slName') === '" + slName + "') { rec = r; return false; }"
                        + "      });"
                        + "      if (!rec) { console.error('SL not found: " + slName + "'); return; }"
                        + "      console.log('SL REC: ' + JSON.stringify(rec.data));"

                        // Select in combo - this should trigger internal handlers
                        + "      combo.select(rec);"
                        + "      combo.fireEvent('select', combo, rec);"

                        + "      setTimeout(function(){"
                        // Get child grid store record at rowIndex
                        + "        var childRec = childGrid.getStore().getAt(" + rowIndex + ");"
                        + "        if (!childRec) { console.error('Child grid record not found'); return; }"

                        // Set all fields from the SL store record into child grid record
                        + "        childRec.set('slCode',      rec.get('slCode'));"
                        + "        childRec.set('slName',      rec.get('slName'));"
                        + "        childRec.set('slid',        rec.get('slid'));"
                        + "        childRec.set('className',   rec.get('className'));"

                        // referenceSiteCode — from slSiteCount > 0 use first site, else use connectedSite
                        // The store record has referenceSiteCode=0 by default; set from rec if available
                        + "        var refSiteCode = rec.get('referenceSiteCode') || rec.get('siteCode') || 0;"
                        + "        var refSiteName = rec.get('referenceSiteName') || rec.get('siteName')  || '';"
                        + "        childRec.set('referenceSiteCode', refSiteCode);"
                        + "        childRec.set('referenceSiteName', refSiteName);"

                        // Also set GL fields from SL record
                        + "        childRec.set('ledgerCode',            rec.get('glCode')                 || 0);"
                        + "        childRec.set('ledgerName',            rec.get('glName')                 || '');"
                        + "        childRec.set('ledgerGroupName',       rec.get('generalLedgerGroupName') || '');"
                        + "        childRec.set('ledgerAbbreviation',    rec.get('generalLedgerAbbre')     || '');"

                        + "        console.log('SET slCode='    + childRec.get('slCode'));"
                        + "        console.log('SET slid='      + childRec.get('slid'));"
                        + "        console.log('SET className=' + childRec.get('className'));"
                        + "        console.log('SET refSite='   + childRec.get('referenceSiteCode'));"

                        // Refresh child grid view
                        + "        childGrid.getView().refreshNode(" + rowIndex + ");"
                        + "        combo.blur();"
                        + "        console.log('--- CHILD GRID ROW UPDATED ---');"
                        + "      }, 500);"
                        + "    }"
                        + "  });"
                        + "}, 300);"
                        + "} catch(e){ console.error('ERROR: ' + e); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== STEP 2 : JS Executed for SL '" + slName + "' ===");

        try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }

        verifyChildGridRecord(driver, rowIndex);
    }

    public static void verifyChildGridRecord(WebDriver driver, int rowIndex) {
        System.out.println("=== VERIFYING CHILD GRID RECORD FOR ROW: " + rowIndex + " ===");

        String script =
                "var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "if (!childGrid) return 'Child grid not found';"
                        + "var rec = childGrid.getStore().getAt(" + rowIndex + ");"
                        + "if (!rec) return 'No record at index " + rowIndex + "';"
                        + "var result = [];"
                        + "result.push('slCode           : ' + rec.get('slCode'));"
                        + "result.push('slName           : ' + rec.get('slName'));"
                        + "result.push('slid             : ' + rec.get('slid'));"
                        + "result.push('className        : ' + rec.get('className'));"
                        + "result.push('referenceSiteCode: ' + rec.get('referenceSiteCode'));"
                        + "result.push('referenceSiteName: ' + rec.get('referenceSiteName'));"
                        + "result.push('ledgerName       : ' + rec.get('ledgerName'));"
                        + "result.push('ledgerGroupName  : ' + rec.get('ledgerGroupName'));"
                        + "result.push('FULL_DATA: '         + JSON.stringify(rec.data));"

                        // Also check what cells show in UI
                        + "var view = childGrid.getView();"
                        + "var row  = view.getRow(" + rowIndex + ");"
                        + "if (row) {"
                        + "  var cells = Ext.fly(row).query('td');"
                        + "  for (var i = 0; i < cells.length; i++) {"
                        + "    var txt = (cells[i].innerText || cells[i].textContent || '').trim();"
                        + "    if (txt) result.push('UI_CELL[' + i + ']: ' + txt);"
                        + "  }"
                        + "}"
                        + "return result.join('\\n');";

        Object result = ((JavascriptExecutor) driver).executeScript(script);
        if (result != null) {
            String[] lines = result.toString().split("\n");
            for (String line : lines) {
                System.out.println("[CHILD_VERIFY] " + line);
            }
        }
        System.out.println("=== CHILD GRID VERIFICATION COMPLETE ===");
    }

    public static void diagnoseReferenceSite(WebDriver driver) {
        String script =
                "var result = [];"
                        + "try {"
                        // Check what's in the SL combo store record
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (combo) {"
                        + "    var slRec = combo.getStore().getAt(0);"
                        + "    if (slRec) {"
                        + "      result.push('=== SL STORE REC FULL DATA ===');"
                        + "      result.push(JSON.stringify(slRec.data));"
                        + "    }"
                        + "  }"

                        // Check child grid record
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  if (childGrid) {"
                        + "    var rec = childGrid.getStore().getAt(0);"
                        + "    if (rec) {"
                        + "      result.push('=== CHILD GRID REC FULL DATA ===');"
                        + "      result.push(JSON.stringify(rec.data));"
                        + "    }"
                        // Check child grid model fields
                        + "    var fields = childGrid.getStore().getModel().getFields();"
                        + "    result.push('=== CHILD GRID MODEL FIELDS ===');"
                        + "    for (var i = 0; i < fields.length; i++) {"
                        + "      result.push('FIELD: ' + fields[i].name);"
                        + "    }"
                        + "  }"

                        // Check main form / owner site info
                        + "  var mainGrid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "  if (mainGrid) {"
                        + "    var mainRec = mainGrid.getStore().getAt(0);"
                        + "    if (mainRec) {"
                        + "      result.push('=== MAIN GRID OWNER SITE DATA ===');"
                        + "      result.push('ownerSite     : ' + mainRec.get('ownerSite'));"
                        + "      result.push('admSite_Code_Owner: ' + mainRec.get('admSite_Code_Owner'));"
                        + "      result.push('ownerSiteShortName: ' + mainRec.get('ownerSiteShortName'));"
                        + "    }"
                        + "  }"

                        // Check if there's a picklist field for owner site with site code
                        + "  var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[name=gslpicklistfieldextended-1221-inputEl]')[0];"
                        + "  if (siteField) {"
                        + "    result.push('=== SITE PICKLIST ===');"
                        + "    result.push('value: ' + siteField.getValue());"
                        + "    result.push('rawValue: ' + siteField.getRawValue());"
                        + "  }"

                        + "} catch(e) { result.push('ERROR: ' + e.message); }"
                        + "return result.join('\\n');";

        Object result = ((JavascriptExecutor) driver).executeScript(script);
        if (result != null) {
            String[] lines = result.toString().split("\n");
            for (String line : lines) {
                System.out.println("[REF_SITE_DIAG] " + line);
            }
        }
    }


    public static void setSubLedger8(WebDriver driver, String slName) {
        System.out.println("=== START: setSubLedger '" + slName + "' ===");

        String script =
                "var result = 'STARTED';"
                        + "try {"

                        // Get child grid
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  if (!childGrid) throw new Error('arvch_grid not found');"

                        // Get child grid row 0 and click the slCode cell to open the combo
                        + "  var view = childGrid.getView();"
                        + "  var row = view.getRow(0);"
                        + "  if (!row) throw new Error('Child grid row 0 not found');"

                        // Find slCode column
                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var slCol = null;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'slCode') { slCol = cols[i]; break; }"
                        + "  }"
                        + "  if (!slCol) throw new Error('slCode column not found');"

                        // Click the cell
                        + "  var cell = view.getCell(view.getRecord(row), slCol);"
                        + "  if (!cell) throw new Error('slCode cell not found');"
                        + "  cell.dom.click();"
                        + "  result = 'CELL_CLICKED';"

                        + "  setTimeout(function() {"
                        + "    var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "    if (!combo) { console.error('slCode combo not found after click'); return; }"

                        + "    combo.getStore().load({"
                        + "      callback: function() {"
                        + "        var store = combo.getStore();"
                        + "        console.log('SL store loaded count=' + store.getCount());"

                        // Search for matching SL
                        + "        var slRec = null;"
                        + "        store.each(function(r) {"
                        + "          if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "        });"
                        + "        if (!slRec) { console.error('SL not found: " + slName + "'); return; }"
                        + "        console.log('SL found: slCode=' + slRec.get('slCode') + ' slid=' + slRec.get('slid'));"
                        + "        console.log('SL full data: ' + JSON.stringify(slRec.data));"

                        // Fire select on combo
                        + "        combo.select(slRec);"
                        + "        combo.fireEvent('select', combo, slRec);"

                        + "        setTimeout(function() {"
                        + "          var rec = childGrid.getStore().getAt(0);"
                        + "          if (!rec) { console.error('child grid rec not found'); return; }"

                        // Set all SL fields
                        + "          rec.set('slCode',    slRec.get('slCode'));"
                        + "          rec.set('slName',    slRec.get('slName'));"
                        + "          rec.set('slid',      slRec.get('slid'));"
                        + "          rec.set('className', slRec.get('className'));"

                        // GL fields
                        + "          rec.set('ledgerCode',           slRec.get('glCode')                 || 0);"
                        + "          rec.set('ledgerName',           slRec.get('glName')                 || '');"
                        + "          rec.set('ledgerGroupName',       slRec.get('generalLedgerGroupName') || '');"
                        + "          rec.set('ledgerAbbreviation',    slRec.get('generalLedgerAbbre')     || '');"

                        // Reference Site — get from SL API response fields if available
                        // The SL API returns referenceSiteCode/referenceSiteName if slSiteCount > 0
                        // Otherwise it defaults to the connected site (admSite_Code_Owner=1, ownerSite=Hena Embroidery)
                        + "          var refSiteCode = slRec.get('referenceSiteCode') || slRec.get('siteCode') || 0;"
                        + "          var refSiteName = slRec.get('referenceSiteName') || slRec.get('siteName')  || '';"

                        // If still empty, fall back to owner site from main grid
                        + "          if (!refSiteCode || refSiteCode === 0) {"
                        + "            var mainGrid = Ext.ComponentQuery.query('gridpanel')[0];"
                        + "            if (mainGrid && mainGrid.getStore().getCount() > 0) {"
                        + "              var mainRec = mainGrid.getStore().getAt(0);"
                        + "              refSiteCode = mainRec.get('admSite_Code_Owner') || 1;"
                        + "              refSiteName = mainRec.get('ownerSite') || '';"
                        + "            } else {"
                        // On Add form, main grid is empty — read from the owner site picklist field
                        + "              var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[fieldLabel=Owner Site]')[0];"
                        + "              if (siteField) {"
                        + "                refSiteCode = siteField.getValue() || 1;"
                        + "                refSiteName = siteField.getRawValue() || '';"
                        + "              }"
                        + "            }"
                        + "          }"
                        + "          console.log('refSiteCode=' + refSiteCode + ' refSiteName=' + refSiteName);"
                        + "          rec.set('referenceSiteCode', refSiteCode);"
                        + "          rec.set('referenceSiteName', refSiteName);"

                        // Refresh child grid row
                        + "          childGrid.getView().refreshNode(0);"
                        + "          combo.blur();"

                        + "          console.log('DONE: slCode='  + rec.get('slCode'));"
                        + "          console.log('DONE: slid='    + rec.get('slid'));"
                        + "          console.log('DONE: class='   + rec.get('className'));"
                        + "          console.log('DONE: refSite=' + rec.get('referenceSiteCode') + ' / ' + rec.get('referenceSiteName'));"
                        + "        }, 500);"
                        + "      }"
                        + "    });"
                        + "  }, 300);"

                        + "} catch(e) { console.error('setSubLedger ERROR: ' + e.message); throw e; }"
                        + "return result;";

        Object result = ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== JS returned: " + result + " ===");

        try { Thread.sleep(2500); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("=== END: setSubLedger ===");
    }

    public static void setSubLedger9(WebDriver driver, String slName) {
        System.out.println("=== START: setSubLedger '" + slName + "' ===");

        String script =
                "try {"
                        + "var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "if (!childGrid) throw new Error('arvch_grid not found');"

                        // Click slCode cell in child grid to trigger editor/combo
                        + "var view = childGrid.getView();"
                        + "var childRec0 = childGrid.getStore().getAt(0);"
                        + "if (!childRec0) throw new Error('child grid has no records');"
                        + "var cols = childGrid.getColumnManager().getColumns();"
                        + "var slCol = null;"
                        + "for (var i=0; i<cols.length; i++) {"
                        + "  if (cols[i].dataIndex === 'slCode') { slCol = cols[i]; break; }"
                        + "}"
                        + "if (!slCol) throw new Error('slCode column not found');"

                        // Use getCell with record + column
                        + "var cellEl = view.getCell(childRec0, slCol);"
                        + "if (!cellEl) throw new Error('slCode cell element not found');"
                        + "cellEl.dom.click();"
                        + "console.log('slCode cell clicked');"

                        + "setTimeout(function() {"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) { console.error('slCode combo not found after cell click'); return; }"
                        + "  console.log('combo found: ' + combo.id);"

                        // Load store with search query for the specific SL name
                        + "  combo.getStore().load({"
                        + "    params: { queryFieldName: ['slName','className'], query: '" + slName + "', page: 1, start: 0, limit: 25 },"
                        + "    callback: function() {"
                        + "      var store = combo.getStore();"
                        + "      console.log('store loaded count=' + store.getCount());"

                        + "      var slRec = null;"
                        + "      store.each(function(r) {"
                        + "        if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "      });"

                        // If exact not found, take first record
                        + "      if (!slRec && store.getCount() > 0) {"
                        + "        slRec = store.getAt(0);"
                        + "        console.log('Exact match not found, using first record: ' + slRec.get('slName'));"
                        + "      }"
                        + "      if (!slRec) { console.error('No SL records found for: " + slName + "'); return; }"

                        + "      console.log('SL full data: ' + JSON.stringify(slRec.data));"

                        // Select in combo
                        + "      combo.select(slRec);"
                        + "      combo.fireEvent('select', combo, slRec);"

                        + "      setTimeout(function() {"
                        + "        var rec = childGrid.getStore().getAt(0);"
                        + "        if (!rec) { console.error('child grid rec not found after select'); return; }"

                        // Set SL fields
                        + "        rec.set('slCode',    slRec.get('slCode'));"
                        + "        rec.set('slName',    slRec.get('slName'));"
                        + "        rec.set('slid',      slRec.get('slid'));"
                        + "        rec.set('className', slRec.get('className'));"

                        // Set GL fields
                        + "        rec.set('ledgerCode',        slRec.get('glCode')                 || 0);"
                        + "        rec.set('ledgerName',         slRec.get('glName')                 || '');"
                        + "        rec.set('ledgerGroupName',    slRec.get('generalLedgerGroupName') || '');"
                        + "        rec.set('ledgerAbbreviation', slRec.get('generalLedgerAbbre')     || '');"

                        // Reference Site — from SL rec or fall back to owner site picklist
                        + "        var refSiteCode = slRec.get('referenceSiteCode') || 0;"
                        + "        var refSiteName = slRec.get('referenceSiteName') || '';"
                        + "        if (!refSiteCode || refSiteCode === 0) {"
                        + "          var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[fieldLabel=Owner Site]')[0];"
                        + "          if (siteField) {"
                        + "            refSiteCode = siteField.getValue()  || 1;"
                        + "            refSiteName = siteField.getRawValue() || '';"
                        + "            console.log('refSite from owner site picklist: code=' + refSiteCode + ' name=' + refSiteName);"
                        + "          }"
                        + "        }"
                        + "        rec.set('referenceSiteCode', refSiteCode);"
                        + "        rec.set('referenceSiteName', refSiteName);"

                        // Refresh UI
                        + "        childGrid.getView().refreshNode(0);"
                        + "        combo.blur();"

                        + "        console.log('DONE slCode='    + rec.get('slCode'));"
                        + "        console.log('DONE slid='      + rec.get('slid'));"
                        + "        console.log('DONE className=' + rec.get('className'));"
                        + "        console.log('DONE refSiteCode=' + rec.get('referenceSiteCode'));"
                        + "        console.log('DONE refSiteName=' + rec.get('referenceSiteName'));"
                        + "        console.log('DONE ledgerName='  + rec.get('ledgerName'));"
                        + "      }, 600);"
                        + "    }"
                        + "  });"
                        + "}, 400);"

                        + "} catch(e) { console.error('setSubLedger ERROR: ' + e.message); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== JS Executed for SL '" + slName + "' ===");

        try { Thread.sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("=== END: setSubLedger ===");
    }


    public static void setSubLedger10(WebDriver driver, String slName) {
        System.out.println("=== START: setSubLedger '" + slName + "' ===");

        String script =
                "try {"
                        + "var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "if (!childGrid) throw new Error('arvch_grid not found');"

                        + "var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "if (!combo) throw new Error('slCode combo not found');"
                        + "console.log('combo found: ' + combo.id);"

                        // Load with search query
                        + "combo.getStore().load({"
                        + "  params: { query: '" + slName + "', page: 1, start: 0, limit: 25 },"
                        + "  callback: function() {"
                        + "    var store = combo.getStore();"
                        + "    console.log('store loaded count=' + store.getCount());"

                        // Find exact match
                        + "    var slRec = null;"
                        + "    store.each(function(r) {"
                        + "      if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "    });"
                        + "    if (!slRec && store.getCount() > 0) {"
                        + "      slRec = store.getAt(0);"
                        + "      console.log('Using first record: ' + slRec.get('slName'));"
                        + "    }"
                        + "    if (!slRec) { console.error('No SL found: " + slName + "'); return; }"
                        + "    console.log('SL data: ' + JSON.stringify(slRec.data));"

                        // Select in combo
                        + "    combo.select(slRec);"
                        + "    combo.fireEvent('select', combo, slRec);"

                        + "    setTimeout(function() {"
                        + "      var rec = childGrid.getStore().getAt(0);"
                        + "      if (!rec) { console.error('child grid rec not found'); return; }"

                        // Set all fields
                        + "      rec.set('slCode',           slRec.get('slCode'));"
                        + "      rec.set('slName',           slRec.get('slName'));"
                        + "      rec.set('slid',             slRec.get('slid'));"
                        + "      rec.set('className',        slRec.get('className'));"
                        + "      rec.set('ledgerCode',       slRec.get('glCode')                 || 0);"
                        + "      rec.set('ledgerName',       slRec.get('glName')                 || '');"
                        + "      rec.set('ledgerGroupName',  slRec.get('generalLedgerGroupName') || '');"
                        + "      rec.set('ledgerAbbreviation', slRec.get('generalLedgerAbbre')   || '');"

                        // Reference Site
                        + "      var refSiteCode = slRec.get('referenceSiteCode') || 0;"
                        + "      var refSiteName = slRec.get('referenceSiteName') || '';"
                        + "      if (!refSiteCode || refSiteCode === 0) {"
                        + "        var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[fieldLabel=Owner Site]')[0];"
                        + "        if (siteField) {"
                        + "          refSiteCode = siteField.getValue()    || 1;"
                        + "          refSiteName = siteField.getRawValue() || '';"
                        + "          console.log('refSite from Owner Site picklist: ' + refSiteCode + ' / ' + refSiteName);"
                        + "        }"
                        + "      }"
                        + "      rec.set('referenceSiteCode', refSiteCode);"
                        + "      rec.set('referenceSiteName', refSiteName);"

                        + "      childGrid.getView().refreshNode(0);"

                        + "      console.log('DONE slCode='      + rec.get('slCode'));"
                        + "      console.log('DONE slid='        + rec.get('slid'));"
                        + "      console.log('DONE className='   + rec.get('className'));"
                        + "      console.log('DONE refSiteCode=' + rec.get('referenceSiteCode'));"
                        + "      console.log('DONE refSiteName=' + rec.get('referenceSiteName'));"
                        + "      console.log('DONE ledgerName='  + rec.get('ledgerName'));"
                        + "    }, 600);"
                        + "  }"
                        + "});"
                        + "} catch(e) { console.error('setSubLedger ERROR: ' + e.message); throw e; }";

        ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== JS Executed for SL '" + slName + "' ===");

        try { Thread.sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("=== END: setSubLedger ===");
    }

    public static void setSubLedgerold(WebDriver driver, String slName) {
        System.out.println("=== START: setSubLedger '" + slName + "' ===");

        // Step 1: Click the slCode cell in child grid to trigger combo rendering
        String clickScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  if (!childGrid) throw new Error('arvch_grid not found');"
                        + "  var view = childGrid.getView();"
                        + "  var rowEl = view.getNode(0);"  // gets the TR DOM element
                        + "  if (!rowEl) throw new Error('child grid row 0 DOM node not found');"
                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var slColIndex = -1;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'slCode') { slColIndex = i; break; }"
                        + "  }"
                        + "  if (slColIndex < 0) throw new Error('slCode column not found');"
                        + "  var cells = rowEl.querySelectorAll('td.x-grid-cell');"
                        + "  console.log('TD cells found: ' + cells.length + ', targeting index: ' + slColIndex);"
                        + "  if (cells.length <= slColIndex) throw new Error('Not enough cells, found: ' + cells.length);"
                        + "  cells[slColIndex].click();"
                        + "  console.log('slCode cell clicked via DOM');"
                        + "  return 'CLICKED';"
                        + "} catch(e) { console.error('click ERROR: ' + e.message); throw e; }";

        Object clickResult = ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("=== Cell click result: " + clickResult + " ===");

        try { Thread.sleep(600); } catch (InterruptedException e) { e.printStackTrace(); }

        // Step 2: Now combo should be rendered - load store and set values
        String selectScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) throw new Error('slCode combo still not found after cell click');"
                        + "  console.log('combo found: ' + combo.id);"

                        + "  combo.getStore().load({"
                        + "    params: { query: '" + slName + "', page: 1, start: 0, limit: 25 },"
                        + "    callback: function() {"
                        + "      var store = combo.getStore();"
                        + "      console.log('store loaded count=' + store.getCount());"

                        + "      var slRec = null;"
                        + "      store.each(function(r) {"
                        + "        if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "      });"
                        + "      if (!slRec && store.getCount() > 0) {"
                        + "        slRec = store.getAt(0);"
                        + "        console.log('Using first record: ' + slRec.get('slName'));"
                        + "      }"
                        + "      if (!slRec) { console.error('No SL found: " + slName + "'); return; }"
                        + "      console.log('SL data: ' + JSON.stringify(slRec.data));"

                        + "      combo.select(slRec);"
                        + "      combo.fireEvent('select', combo, slRec);"

                        + "      setTimeout(function() {"
                        + "        var rec = childGrid.getStore().getAt(0);"
                        + "        if (!rec) { console.error('child grid rec not found'); return; }"

                        + "        rec.set('slCode',             slRec.get('slCode'));"
                        + "        rec.set('slName',             slRec.get('slName'));"
                        + "        rec.set('slid',               slRec.get('slid'));"
                        + "        rec.set('className',          slRec.get('className'));"
                        + "        rec.set('ledgerCode',         slRec.get('glCode')                 || 0);"
                        + "        rec.set('ledgerName',         slRec.get('glName')                 || '');"
                        + "        rec.set('ledgerGroupName',    slRec.get('generalLedgerGroupName') || '');"
                        + "        rec.set('ledgerAbbreviation', slRec.get('generalLedgerAbbre')     || '');"

                        + "        var refSiteCode = slRec.get('referenceSiteCode') || 0;"
                        + "        var refSiteName = slRec.get('referenceSiteName') || '';"
                        + "        if (!refSiteCode || refSiteCode === 0) {"
                        + "          var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[fieldLabel=Owner Site]')[0];"
                        + "          if (siteField) {"
                        + "            refSiteCode = siteField.getValue()    || 1;"
                        + "            refSiteName = siteField.getRawValue() || '';"
                        + "            console.log('refSite from Owner Site: ' + refSiteCode + ' / ' + refSiteName);"
                        + "          }"
                        + "        }"
                        + "        rec.set('referenceSiteCode', refSiteCode);"
                        + "        rec.set('referenceSiteName', refSiteName);"

                        + "        childGrid.getView().refreshNode(0);"

                        + "        console.log('DONE slCode='      + rec.get('slCode'));"
                        + "        console.log('DONE slid='        + rec.get('slid'));"
                        + "        console.log('DONE className='   + rec.get('className'));"
                        + "        console.log('DONE refSiteCode=' + rec.get('referenceSiteCode'));"
                        + "        console.log('DONE refSiteName=' + rec.get('referenceSiteName'));"
                        + "        console.log('DONE ledgerName='  + rec.get('ledgerName'));"
                        + "      }, 600);"
                        + "    }"
                        + "  });"
                        + "} catch(e) { console.error('select ERROR: ' + e.message); throw e; }";

        ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("=== JS select executed for SL '" + slName + "' ===");

        try { Thread.sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("=== END: setSubLedger ===");
    }


    public static void setSubLedger2(WebDriver driver, String slName) {
        System.out.println("=== START: setSubLedger '" + slName + "' ===");

        String clickScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  if (!childGrid) throw new Error('arvch_grid not found');"
                        + "  var view = childGrid.getView();"
                        + "  var rowEl = view.getNode(0);"
                        + "  if (!rowEl) throw new Error('child grid row 0 DOM node not found');"
                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var slColIndex = -1;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'slCode') { slColIndex = i; break; }"
                        + "  }"
                        + "  if (slColIndex < 0) throw new Error('slCode column not found');"
                        + "  var cells = rowEl.querySelectorAll('td.x-grid-cell');"
                        + "  console.log('TD cells found: ' + cells.length + ', targeting index: ' + slColIndex);"
                        + "  if (cells.length <= slColIndex) throw new Error('Not enough cells, found: ' + cells.length);"
                        + "  cells[slColIndex].click();"
                        + "  console.log('slCode cell clicked via DOM');"
                        + "  return 'CLICKED';"
                        + "} catch(e) { console.error('click ERROR: ' + e.message); throw e; }";

        Object clickResult = ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("=== Cell click result: " + clickResult + " ===");

        try { Thread.sleep(600); } catch (InterruptedException e) { e.printStackTrace(); }

        String selectScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) throw new Error('slCode combo still not found after cell click');"
                        + "  console.log('combo found: ' + combo.id);"

                        + "  combo.getStore().load({"
                        + "    params: { query: '" + slName + "', page: 1, start: 0, limit: 25 },"
                        + "    callback: function() {"
                        + "      var store = combo.getStore();"
                        + "      console.log('store loaded count=' + store.getCount());"

                        + "      var slRec = null;"
                        + "      store.each(function(r) {"
                        + "        if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "      });"
                        + "      if (!slRec && store.getCount() > 0) {"
                        + "        slRec = store.getAt(0);"
                        + "        console.log('Using first record: ' + slRec.get('slName'));"
                        + "      }"
                        + "      if (!slRec) { console.error('No SL found: " + slName + "'); return; }"
                        + "      console.log('SL combo ALL fields: ' + JSON.stringify(slRec.data));"

                        + "      combo.select(slRec);"
                        + "      combo.fireEvent('select', combo, slRec);"

                        + "      setTimeout(function() {"
                        + "        var rec = childGrid.getStore().getAt(0);"
                        + "        if (!rec) { console.error('child grid rec not found'); return; }"

                        + "        rec.set('slCode',             slRec.get('slCode'));"
                        + "        rec.set('slName',             slRec.get('slName'));"
                        + "        rec.set('slid',               slRec.get('slid'));"
                        + "        rec.set('className',          slRec.get('className'));"
                        + "        rec.set('ledgerCode',         slRec.get('glCode')                 || 0);"
                        + "        rec.set('ledgerName',         slRec.get('glName')                 || '');"
                        + "        rec.set('ledgerGroupName',    slRec.get('generalLedgerGroupName') || '');"
                        + "        rec.set('ledgerAbbreviation', slRec.get('generalLedgerAbbre')     || '');"

                        + "        var refSiteCode = slRec.get('referenceSiteCode') || 0;"
                        + "        var refSiteName = slRec.get('referenceSiteName') || '';"
                        + "        if (!refSiteCode || refSiteCode === 0) {"
                        + "          var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[fieldLabel=Owner Site]')[0];"
                        + "          if (siteField) {"
                        + "            refSiteCode = siteField.getValue()    || 1;"
                        + "            refSiteName = siteField.getRawValue() || '';"
                        + "            console.log('refSite from Owner Site: ' + refSiteCode + ' / ' + refSiteName);"
                        + "          }"
                        + "        }"

                        + "        rec.set('referenceSiteCode',   refSiteCode);"
                        + "        rec.set('referenceSiteName',   refSiteName);"

                        // ── NEW: set referenceSiteType and referenceSiteOUName ──
                        + "        rec.set('referenceSiteType',   slRec.get('referenceSiteType')   || slRec.get('siteType')   || null);"
                        + "        rec.set('referenceSiteOUName', slRec.get('referenceSiteOUName') || slRec.get('ouName')     || null);"

                        + "        childGrid.getView().refreshNode(0);"

                        + "        console.log('DONE slCode='          + rec.get('slCode'));"
                        + "        console.log('DONE slid='            + rec.get('slid'));"
                        + "        console.log('DONE className='       + rec.get('className'));"
                        + "        console.log('DONE refSiteCode='     + rec.get('referenceSiteCode'));"
                        + "        console.log('DONE refSiteName='     + rec.get('referenceSiteName'));"
                        + "        console.log('DONE refSiteType='     + rec.get('referenceSiteType'));"
                        + "        console.log('DONE refSiteOUName='   + rec.get('referenceSiteOUName'));"
                        + "        console.log('DONE ledgerName='      + rec.get('ledgerName'));"
                        + "      }, 600);"
                        + "    }"
                        + "  });"
                        + "} catch(e) { console.error('select ERROR: ' + e.message); throw e; }";

        ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("=== JS select executed for SL '" + slName + "' ===");

        try { Thread.sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("=== END: setSubLedger ===");
    }



    public static void setSubLedgerVerify(String gridItemId) {
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        String payload = (String) js.executeScript(
                "try {" +
                        "    var g = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];" +
                        "    if (!g) return '" + gridItemId + " not found';" +
                        "    var store = g.getStore();" +
                        "    if (!store || store.getCount() === 0) return 'NO_RECORDS_IN_" + gridItemId + "';" +
                        "    var rec = store.getAt(0);" +
                        "    return JSON.stringify(rec.getData());" +
                        "} catch(e) { return 'ERROR: ' + e.message; }"
        );
        ExtentLogger.info("=== DETAIL RECORD BEFORE SAVE [" + gridItemId + "] === " + payload + " === END ===");
    }

    public static void debugSubLedgerRecords(WebDriver driver) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String result = (String) js.executeScript(
                "try {" +
                        "    var result = {};" +
                        "    var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];" +
                        "    if (combo) {" +
                        "        var comboStore = combo.getStore();" +
                        "        result.comboRecord = comboStore && comboStore.getCount() > 0" +
                        "            ? comboStore.getAt(0).data : 'store empty';" +
                        "    } else {" +
                        "        result.comboRecord = 'combo not found';" +
                        "    }" +

                        // ── FIX: always use arvch_grid, not first grid with records ──
                        "    var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];" +
                        "    if (childGrid) {" +
                        "        var gridStore = childGrid.getStore();" +
                        "        result.gridRecord = gridStore && gridStore.getCount() > 0" +
                        "            ? gridStore.getAt(0).data : 'store empty';" +
                        "    } else {" +
                        "        result.gridRecord = 'arvch_grid not found';" +
                        "    }" +

                        "    return JSON.stringify(result);" +
                        "} catch(e) {" +
                        "    return 'ERROR: ' + e.message;" +
                        "}"
        );

        System.out.println("=== DEBUG SUB LEDGER RECORDS ===");
        System.out.println(result);
        System.out.println("=== END DEBUG ===");
    }




    public static void setSubLedger5(WebDriver driver, String slName) {
        System.out.println("=== START: setSubLedger '" + slName + "' ===");

        String clickScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  if (!childGrid) throw new Error('arvch_grid not found');"
                        + "  var view = childGrid.getView();"
                        + "  var rowEl = view.getNode(0);"
                        + "  if (!rowEl) throw new Error('child grid row 0 DOM node not found');"
                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var slColIndex = -1;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'slCode') { slColIndex = i; break; }"
                        + "  }"
                        + "  if (slColIndex < 0) throw new Error('slCode column not found');"
                        + "  var cells = rowEl.querySelectorAll('td.x-grid-cell');"
                        + "  if (cells.length <= slColIndex) throw new Error('Not enough cells, found: ' + cells.length);"
                        + "  cells[slColIndex].click();"
                        + "  return 'CLICKED';"
                        + "} catch(e) { throw e; }";

        Object clickResult = ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("=== Cell click result: " + clickResult + " ===");

        try { Thread.sleep(600); } catch (InterruptedException e) { e.printStackTrace(); }

        // Step 1: Load combo store and collect ALL data synchronously
        String loadScript =
                "try {"
                        + "  var result = {};"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) return JSON.stringify({error: 'combo not found'});"
                        + "  result.comboFound = combo.id;"

                        + "  var store = combo.getStore();"
                        + "  result.storeCount = store.getCount();"

                        // Find matching record
                        + "  var slRec = null;"
                        + "  store.each(function(r) {"
                        + "    if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "  });"
                        + "  if (!slRec && store.getCount() > 0) slRec = store.getAt(0);"
                        + "  if (!slRec) return JSON.stringify({error: 'no SL record found'});"

                        + "  result.comboRecord = slRec.data;"
                        + "  return JSON.stringify(result);"
                        + "} catch(e) { return JSON.stringify({error: e.message}); }";

        String loadResult = (String) ((JavascriptExecutor) driver).executeScript(loadScript);
        System.out.println("=== COMBO LOAD RESULT ===");
        System.out.println(loadResult);
        System.out.println("=== END COMBO LOAD RESULT ===");

        // Step 2: Select and set all fields — return debug log as string
        String selectScript =
                "try {"
                        + "  var logs = [];"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) return 'ERROR: combo not found';"

                        + "  var store = combo.getStore();"
                        + "  var slRec = null;"
                        + "  store.each(function(r) {"
                        + "    if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "  });"
                        + "  if (!slRec && store.getCount() > 0) slRec = store.getAt(0);"
                        + "  if (!slRec) return 'ERROR: no SL record';"

                        + "  combo.select(slRec);"
                        + "  combo.fireEvent('select', combo, slRec);"

                        + "  var rec = childGrid.getStore().getAt(0);"
                        + "  if (!rec) return 'ERROR: child grid rec not found';"

                        + "  rec.set('slCode',             slRec.get('slCode'));"
                        + "  rec.set('slName',             slRec.get('slName'));"
                        + "  rec.set('slid',               slRec.get('slid'));"
                        + "  rec.set('className',          slRec.get('className'));"
                        + "  rec.set('ledgerCode',         slRec.get('glCode')                 || 0);"
                        + "  rec.set('ledgerName',         slRec.get('glName')                 || '');"
                        + "  rec.set('ledgerGroupName',    slRec.get('generalLedgerGroupName') || '');"
                        + "  rec.set('ledgerAbbreviation', slRec.get('generalLedgerAbbre')     || '');"

                        // referenceSiteCode — try multiple sources
                        + "  var refSiteCode = slRec.get('referenceSiteCode') || 0;"
                        + "  var refSiteName = slRec.get('referenceSiteName') || '';"
                        + "  logs.push('refSiteCode from combo: ' + refSiteCode);"

                        + "  if (!refSiteCode || refSiteCode === 0) {"
                        + "    var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[fieldLabel=Owner Site]')[0];"
                        + "    if (siteField) {"
                        + "      refSiteCode = siteField.getValue() || 0;"
                        + "      refSiteName = siteField.getRawValue() || refSiteName;"
                        + "      logs.push('refSiteCode from Owner Site picklist: ' + refSiteCode + ' / ' + refSiteName);"
                        + "    }"
                        + "  }"

                        + "  if (!refSiteCode || refSiteCode === 0) {"
                        + "    var connSite = Ext.ComponentQuery.query('[name=connectedSiteCode]')[0]"
                        + "                || Ext.ComponentQuery.query('[name=ownerSiteCode]')[0];"
                        + "    if (connSite) {"
                        + "      refSiteCode = connSite.getValue() || 0;"
                        + "      logs.push('refSiteCode from field: ' + refSiteCode);"
                        + "    }"
                        + "  }"

                        + "  if (!refSiteCode || refSiteCode === 0) {"
                        + "    refSiteCode = 1;"
                        + "    logs.push('refSiteCode FALLBACK to 1');"
                        + "  }"

                        // referenceSiteType and referenceSiteOUName
                        + "  var refSiteType   = slRec.get('referenceSiteType')   || slRec.get('siteType')   || 'OU';"
                        + "  var refSiteOUName = slRec.get('referenceSiteOUName') || slRec.get('ouName')     || refSiteName || '';"
                        + "  logs.push('refSiteType initial: ' + refSiteType);"
                        + "  logs.push('refSiteOUName initial: ' + refSiteOUName);"

                        // Search StoreManager for better values
                        + "  Ext.data.StoreManager.each(function(s) {"
                        + "    if (s.getCount() > 0) {"
                        + "      var r = s.findRecord('code', refSiteCode);"
                        + "      if (!r) r = s.findRecord('siteCode', refSiteCode);"
                        + "      if (r) {"
                        + "        refSiteType   = r.get('siteType')   || r.get('type')   || refSiteType;"
                        + "        refSiteOUName = r.get('ouName')     || r.get('OUName') || refSiteOUName;"
                        + "        logs.push('Found in StoreManager: siteType=' + refSiteType + ' ouName=' + refSiteOUName);"
                        + "        logs.push('StoreManager record ALL fields: ' + JSON.stringify(r.data));"
                        + "      }"
                        + "    }"
                        + "  });"

                        + "  rec.set('referenceSiteCode',   refSiteCode);"
                        + "  rec.set('referenceSiteName',   refSiteName || 'Hena Embroidery');"
                        + "  rec.set('referenceSiteType',   refSiteType);"
                        + "  rec.set('referenceSiteOUName', refSiteOUName);"

                        + "  childGrid.getView().refreshNode(0);"

                        + "  logs.push('FINAL slCode='        + rec.get('slCode'));"
                        + "  logs.push('FINAL slName='        + rec.get('slName'));"
                        + "  logs.push('FINAL ledgerCode='    + rec.get('ledgerCode'));"
                        + "  logs.push('FINAL ledgerName='    + rec.get('ledgerName'));"
                        + "  logs.push('FINAL refSiteCode='   + rec.get('referenceSiteCode'));"
                        + "  logs.push('FINAL refSiteName='   + rec.get('referenceSiteName'));"
                        + "  logs.push('FINAL refSiteType='   + rec.get('referenceSiteType'));"
                        + "  logs.push('FINAL refSiteOUName=' + rec.get('referenceSiteOUName'));"
                        + "  logs.push('FULL GRID RECORD: '   + JSON.stringify(rec.getData()));"

                        + "  return logs.join('\\n');"
                        + "} catch(e) { return 'ERROR: ' + e.message; }";

        String selectResult = (String) ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("=== SELECT RESULT ===");
        System.out.println(selectResult);
        System.out.println("=== END SELECT RESULT ===");

        try { Thread.sleep(2000); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("=== END: setSubLedger ===");
    }

    public static void setSubLedger6(WebDriver driver, String slName) {
        System.out.println("=== START: setSubLedger '" + slName + "' ===");

        String clickScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  if (!childGrid) throw new Error('arvch_grid not found');"
                        + "  var view = childGrid.getView();"
                        + "  var rowEl = view.getNode(0);"
                        + "  if (!rowEl) throw new Error('child grid row 0 DOM node not found');"
                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var slColIndex = -1;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'slCode') { slColIndex = i; break; }"
                        + "  }"
                        + "  if (slColIndex < 0) throw new Error('slCode column not found');"
                        + "  var cells = rowEl.querySelectorAll('td.x-grid-cell');"
                        + "  if (cells.length <= slColIndex) throw new Error('Not enough cells: ' + cells.length);"
                        + "  cells[slColIndex].click();"
                        + "  return 'CLICKED';"
                        + "} catch(e) { throw e; }";

        Object clickResult = ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("=== Cell click result: " + clickResult + " ===");

        try { Thread.sleep(600); } catch (InterruptedException e) { e.printStackTrace(); }

        String selectScript =
                "try {"
                        + "  window.__slDebugLog = [];"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) throw new Error('slCode combo not found');"
                        + "  window.__slDebugLog.push('combo found: ' + combo.id);"

                        // ── MUST use store.load() — combo store is empty until loaded ──
                        + "  combo.getStore().load({"
                        + "    params: { query: '" + slName + "', page: 1, start: 0, limit: 25 },"
                        + "    callback: function() {"
                        + "      var store = combo.getStore();"
                        + "      window.__slDebugLog.push('store loaded count=' + store.getCount());"

                        + "      var slRec = null;"
                        + "      store.each(function(r) {"
                        + "        if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "      });"
                        + "      if (!slRec && store.getCount() > 0) {"
                        + "        slRec = store.getAt(0);"
                        + "        window.__slDebugLog.push('Using first record: ' + slRec.get('slName'));"
                        + "      }"
                        + "      if (!slRec) {"
                        + "        window.__slDebugLog.push('ERROR: No SL found');"
                        + "        return;"
                        + "      }"
                        + "      window.__slDebugLog.push('SL combo ALL fields: ' + JSON.stringify(slRec.data));"

                        // combo.select fires app's own handler which sets slName in grid
                        + "      combo.select(slRec);"
                        + "      combo.fireEvent('select', combo, slRec);"

                        + "      setTimeout(function() {"
                        + "        var rec = childGrid.getStore().getAt(0);"
                        + "        if (!rec) {"
                        + "          window.__slDebugLog.push('ERROR: child grid rec not found');"
                        + "          return;"
                        + "        }"

                        + "        window.__slDebugLog.push('slName after combo.select: ' + rec.get('slName'));"

                        // Only set SL fields if app handler didn't set them
                        + "        if (!rec.get('slName') || rec.get('slName') === '') {"
                        + "          rec.set('slCode',             slRec.get('slCode'));"
                        + "          rec.set('slName',             slRec.get('slName'));"
                        + "          rec.set('slid',               slRec.get('slid'));"
                        + "          rec.set('className',          slRec.get('className'));"
                        + "          rec.set('ledgerCode',         slRec.get('glCode')                 || 0);"
                        + "          rec.set('ledgerName',         slRec.get('glName')                 || '');"
                        + "          rec.set('ledgerGroupName',    slRec.get('generalLedgerGroupName') || '');"
                        + "          rec.set('ledgerAbbreviation', slRec.get('generalLedgerAbbre')     || '');"
                        + "          window.__slDebugLog.push('SL fields set manually');"
                        + "        } else {"
                        + "          window.__slDebugLog.push('App handler set slName correctly');"
                        + "        }"

                        // Always set reference site fields — app never sets these
                        + "        var refSiteCode = slRec.get('referenceSiteCode') || 0;"
                        + "        var refSiteName = slRec.get('referenceSiteName') || '';"
                        + "        window.__slDebugLog.push('refSiteCode from combo: ' + refSiteCode);"

                        + "        if (!refSiteCode || refSiteCode === 0) {"
                        + "          var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[fieldLabel=Owner Site]')[0];"
                        + "          if (siteField) {"
                        + "            refSiteCode = siteField.getValue() || 0;"
                        + "            refSiteName = siteField.getRawValue() || refSiteName;"
                        + "            window.__slDebugLog.push('refSiteCode from Owner Site: ' + refSiteCode + ' / ' + refSiteName);"
                        + "          }"
                        + "        }"

                        + "        if (!refSiteCode || refSiteCode === 0) {"
                        + "          var connSite = Ext.ComponentQuery.query('[name=connectedSiteCode]')[0]"
                        + "                      || Ext.ComponentQuery.query('[name=ownerSiteCode]')[0];"
                        + "          if (connSite) {"
                        + "            refSiteCode = connSite.getValue() || 0;"
                        + "            window.__slDebugLog.push('refSiteCode from field: ' + refSiteCode);"
                        + "          }"
                        + "        }"

                        + "        if (!refSiteCode || refSiteCode === 0) {"
                        + "          refSiteCode = 1;"
                        + "          window.__slDebugLog.push('refSiteCode FALLBACK to 1');"
                        + "        }"

                        + "        var refSiteType   = slRec.get('referenceSiteType')   || slRec.get('siteType')   || 'OU';"
                        + "        var refSiteOUName = slRec.get('referenceSiteOUName') || slRec.get('ouName')     || refSiteName || '';"

                        + "        Ext.data.StoreManager.each(function(s) {"
                        + "          if (s.getCount() > 0) {"
                        + "            var r = s.findRecord('code', refSiteCode);"
                        + "            if (!r) r = s.findRecord('siteCode', refSiteCode);"
                        + "            if (r) {"
                        + "              refSiteType   = r.get('siteType')   || r.get('type')   || refSiteType;"
                        + "              refSiteOUName = r.get('ouName')     || r.get('OUName') || refSiteOUName;"
                        + "              window.__slDebugLog.push('StoreManager site: type=' + refSiteType + ' ouName=' + refSiteOUName);"
                        + "              window.__slDebugLog.push('Site ALL fields: ' + JSON.stringify(r.data));"
                        + "            }"
                        + "          }"
                        + "        });"

                        + "        rec.set('referenceSiteCode',   refSiteCode);"
                        + "        rec.set('referenceSiteName',   refSiteName || '');"
                        + "        rec.set('referenceSiteType',   refSiteType);"
                        + "        rec.set('referenceSiteOUName', refSiteOUName);"

                        + "        childGrid.getView().refreshNode(0);"

                        + "        window.__slDebugLog.push('FINAL slCode='        + rec.get('slCode'));"
                        + "        window.__slDebugLog.push('FINAL slName='        + rec.get('slName'));"
                        + "        window.__slDebugLog.push('FINAL ledgerName='    + rec.get('ledgerName'));"
                        + "        window.__slDebugLog.push('FINAL refSiteCode='   + rec.get('referenceSiteCode'));"
                        + "        window.__slDebugLog.push('FINAL refSiteName='   + rec.get('referenceSiteName'));"
                        + "        window.__slDebugLog.push('FINAL refSiteType='   + rec.get('referenceSiteType'));"
                        + "        window.__slDebugLog.push('FINAL refSiteOUName=' + rec.get('referenceSiteOUName'));"
                        + "        window.__slDebugLog.push('FULL GRID RECORD: '   + JSON.stringify(rec.getData()));"
                        + "      }, 600);"
                        + "    }"
                        + "  });"
                        + "} catch(e) { window.__slDebugLog.push('ERROR: ' + e.message); }";

        ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("=== JS select executed for SL '" + slName + "' ===");

        // Wait for store.load callback + setTimeout(600) to fully complete
        try { Thread.sleep(4000); } catch (InterruptedException e) { e.printStackTrace(); }

        // Read all async logs synchronously from window variable
        String debugLog = (String) ((JavascriptExecutor) driver).executeScript(
                "return window.__slDebugLog ? window.__slDebugLog.join('\\n') : 'no logs captured';"
        );
        System.out.println("=== ASYNC DEBUG LOG ===");
        System.out.println(debugLog);
        System.out.println("=== END ASYNC DEBUG LOG ===");

        System.out.println("=== END: setSubLedger ===");
    }

    public static void setSubLedger7(WebDriver driver, String slName) {
        System.out.println("=== START: setSubLedger '" + slName + "' ===");

        String clickScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  if (!childGrid) throw new Error('arvch_grid not found');"
                        + "  var view = childGrid.getView();"
                        + "  var rowEl = view.getNode(0);"
                        + "  if (!rowEl) throw new Error('child grid row 0 DOM node not found');"
                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var slColIndex = -1;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'slCode') { slColIndex = i; break; }"
                        + "  }"
                        + "  if (slColIndex < 0) throw new Error('slCode column not found');"
                        + "  var cells = rowEl.querySelectorAll('td.x-grid-cell');"
                        + "  console.log('TD cells found: ' + cells.length + ', targeting index: ' + slColIndex);"
                        + "  if (cells.length <= slColIndex) throw new Error('Not enough cells, found: ' + cells.length);"
                        + "  cells[slColIndex].click();"
                        + "  console.log('slCode cell clicked via DOM');"
                        + "  return 'CLICKED';"
                        + "} catch(e) { console.error('click ERROR: ' + e.message); throw e; }";

        Object clickResult = ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("=== Cell click result: " + clickResult + " ===");

        try { Thread.sleep(600); } catch (InterruptedException e) { e.printStackTrace(); }

        String selectScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=arvch_grid]')[0];"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) throw new Error('slCode combo still not found after cell click');"
                        + "  console.log('combo found: ' + combo.id);"

                        + "  combo.getStore().load({"
                        + "    params: { query: '" + slName + "', page: 1, start: 0, limit: 25 },"
                        + "    callback: function() {"
                        + "      var store = combo.getStore();"
                        + "      console.log('store loaded count=' + store.getCount());"

                        + "      var slRec = null;"
                        + "      store.each(function(r) {"
                        + "        if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "      });"
                        + "      if (!slRec && store.getCount() > 0) {"
                        + "        slRec = store.getAt(0);"
                        + "        console.log('Using first record: ' + slRec.get('slName'));"
                        + "      }"
                        + "      if (!slRec) { console.error('No SL found: " + slName + "'); return; }"
                        + "      console.log('SL data: ' + JSON.stringify(slRec.data));"

                        + "      combo.select(slRec);"
                        + "      combo.fireEvent('select', combo, slRec);"

                        + "      setTimeout(function() {"
                        + "        var rec = childGrid.getStore().getAt(0);"
                        + "        if (!rec) { console.error('child grid rec not found'); return; }"

                        // ── ORIGINAL: always set ALL fields unconditionally ──
                        + "        rec.set('slCode',             slRec.get('slCode'));"
                        + "        rec.set('slName',             slRec.get('slName'));"
                        + "        rec.set('slid',               slRec.get('slid'));"
                        + "        rec.set('className',          slRec.get('className'));"
                        + "        rec.set('ledgerCode',         slRec.get('glCode')                 || 0);"
                        + "        rec.set('ledgerName',         slRec.get('glName')                 || '');"
                        + "        rec.set('ledgerGroupName',    slRec.get('generalLedgerGroupName') || '');"
                        + "        rec.set('ledgerAbbreviation', slRec.get('generalLedgerAbbre')     || '');"

                        // ── ORIGINAL: refSiteCode using getValue() || 1 ──
                        + "        var refSiteCode = slRec.get('referenceSiteCode') || 0;"
                        + "        var refSiteName = slRec.get('referenceSiteName') || '';"
                        + "        if (!refSiteCode || refSiteCode === 0) {"
                        + "          var siteField = Ext.ComponentQuery.query('gslpicklistfieldextended[fieldLabel=Owner Site]')[0];"
                        + "          if (siteField) {"
                        + "            refSiteCode = siteField.getValue()    || 1;"
                        + "            refSiteName = siteField.getRawValue() || '';"
                        + "            console.log('refSite from Owner Site: ' + refSiteCode + ' / ' + refSiteName);"
                        + "          }"
                        + "        }"

                        + "        rec.set('referenceSiteCode', refSiteCode);"
                        + "        rec.set('referenceSiteName', refSiteName);"

                        // ── NEW: only these two lines added to original ──
                        + "        rec.set('referenceSiteType',   slRec.get('referenceSiteType')   || slRec.get('siteType')   || 'OU');"
                        + "        rec.set('referenceSiteOUName', slRec.get('referenceSiteOUName') || slRec.get('ouName')     || refSiteName || '');"

                        + "        childGrid.getView().refreshNode(0);"

                        + "        console.log('DONE slCode='          + rec.get('slCode'));"
                        + "        console.log('DONE slid='            + rec.get('slid'));"
                        + "        console.log('DONE className='       + rec.get('className'));"
                        + "        console.log('DONE refSiteCode='     + rec.get('referenceSiteCode'));"
                        + "        console.log('DONE refSiteName='     + rec.get('referenceSiteName'));"
                        + "        console.log('DONE refSiteType='     + rec.get('referenceSiteType'));"
                        + "        console.log('DONE refSiteOUName='   + rec.get('referenceSiteOUName'));"
                        + "        console.log('DONE ledgerName='      + rec.get('ledgerName'));"
                        + "      }, 600);"
                        + "    }"
                        + "  });"
                        + "} catch(e) { console.error('select ERROR: ' + e.message); throw e; }";

        ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("=== JS select executed for SL '" + slName + "' ===");

        try { Thread.sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }
        System.out.println("=== END: setSubLedger ===");
    }


    public static void setSubLedger(WebDriver driver, String slName, String slId, String referenceSite) {
        setSubLedger(driver, slName, slId, referenceSite, "arvch_grid");
    }

    /**
     * NATIVE sub-ledger selection support: open the inline Sub-Ledger combo editor on the
     * given grid row and return the combo's real {@code inputEl} DOM id so Selenium can type
     * into it and pick from the dropdown via genuine user interaction.
     *
     * <p>Unlike {@link #setSubLedger}, this does NOT inject values with {@code rec.set} — it
     * only opens the editor. The caller types the name and clicks a real {@code <li role=option>},
     * which fires the application's own combo {@code select} handler, triggering the GL + TDS
     * applicability evaluation that injection bypasses (so TDS-deduction rows render the
     * "Calc. Amount" button instead of "TDS not appl.").</p>
     *
     * @return the combo input element id, or {@code null} if the editor could not be opened.
     */
    public static String openSubLedgerComboEditor(WebDriver driver, int rowIndex, String gridItemId) {
        String clickScript =
                "try {" +
                "  var g = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];" +
                "  if (!g) return 'ERR:grid';" +
                "  var rowEl = g.getView().getNode(" + rowIndex + ");" +
                "  if (!rowEl) return 'ERR:row';" +
                "  var cols = g.getColumnManager().getColumns();" +
                "  var idx = -1; for (var i=0;i<cols.length;i++){ if(cols[i].dataIndex==='slCode'){ idx=i; break; } }" +
                "  if (idx < 0) return 'ERR:col';" +
                "  var cells = rowEl.querySelectorAll('td.x-grid-cell');" +
                "  if (cells.length <= idx) return 'ERR:cells';" +
                "  cells[idx].click();" +
                "  return 'CLICKED';" +
                "} catch(e){ return 'ERR:' + e.message; }";
        Object clicked = ((JavascriptExecutor) driver).executeScript(clickScript);
        if (!"CLICKED".equals(String.valueOf(clicked))) {
            ExtentLogger.warning("[SL_NATIVE] Could not open Sub-Ledger editor on row " + rowIndex + " — " + clicked);
            return null;
        }
        try { Thread.sleep(600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        String idScript =
                "try {" +
                "  var c = Ext.ComponentQuery.query('combo[name=slCode]')[0];" +
                "  if (!c) return '';" +
                "  c.expand();" +
                "  return (c.inputEl && c.inputEl.dom) ? c.inputEl.dom.id : '';" +
                "} catch(e){ return ''; }";
        Object inputId = ((JavascriptExecutor) driver).executeScript(idScript);
        String id = (inputId == null) ? "" : String.valueOf(inputId);
        if (id.isEmpty()) {
            ExtentLogger.warning("[SL_NATIVE] Sub-Ledger combo input id not found after opening editor on row " + rowIndex);
            return null;
        }
        ExtentLogger.info("[SL_NATIVE] Sub-Ledger editor open on row " + rowIndex + " — inputId=" + id);
        return id;
    }

    public static void setSubLedger(WebDriver driver, String slName, String slId, String referenceSite, String gridItemId) {
        slName = (slName != null) ? slName.trim() : "";
        slId   = (slId   != null) ? slId.trim()   : "";
        if (slName.isEmpty()) {
            throw new IllegalArgumentException(
                "setSubLedger called with null/empty slName (slId='" + slId + "'). "
                + "Check that the data source (Excel or DB) is providing a non-empty Sub Ledger name."
            );
        }
        System.out.println("=== START: setSubLedger '" + slName + "' id='" + slId + "' ===");
        String mode = slId.isEmpty() ? "NAME_ONLY" : "NAME_AND_ID";
        String safeSlId = slId;

        String clickScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];"
                        + "  if (!childGrid) throw new Error('" + gridItemId + " not found');"
                        + "  var view = childGrid.getView();"
                        + "  var rowEl = view.getNode(0);"
                        + "  if (!rowEl) throw new Error('child grid row 0 DOM node not found');"
                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var slColIndex = -1;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'slCode') { slColIndex = i; break; }"
                        + "  }"
                        + "  if (slColIndex < 0) throw new Error('slCode column not found');"
                        + "  var cells = rowEl.querySelectorAll('td.x-grid-cell');"
                        + "  console.log('TD cells found: ' + cells.length + ', targeting index: ' + slColIndex);"
                        + "  if (cells.length <= slColIndex) throw new Error('Not enough cells, found: ' + cells.length);"
                        + "  cells[slColIndex].click();"
                        + "  console.log('slCode cell clicked via DOM');"
                        + "  return 'CLICKED';"
                        + "} catch(e) { console.error('click ERROR: ' + e.message); throw e; }";

        Object clickResult = ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("=== Cell click result: " + clickResult + " ===");

        try { Thread.sleep(600); } catch (InterruptedException e) { e.printStackTrace(); }

        String selectScript =
                "window.__slSearchDone = false; window.__slSearchFound = false;"
                        + "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) throw new Error('slCode combo still not found after cell click');"
                        + "  console.log('combo found: ' + combo.id);"
                        + "  var PAGE_SIZE = 25;"
                        + "  function loadAndSearch(pageStart) {"
                        + "    combo.getStore().load({"
                        + "      params: { queryFieldName: ['slName','className'], query: '" + slName + "', page: Math.floor(pageStart / PAGE_SIZE) + 1, start: pageStart, limit: PAGE_SIZE },"
                        + "      callback: function() {"
                        + "        var store = combo.getStore();"
                        + "        console.log('Page walk: page=' + (Math.floor(pageStart / PAGE_SIZE) + 1) + ' start=' + pageStart + ' loaded=' + store.getCount() + ' total=' + store.getTotalCount());"
                        + "        var slRec = null;"
                        + "        if ('" + safeSlId + "' !== '') {"
                        + "          store.each(function(r) {"
                        + "            if (r.get('slName') === '" + slName + "' && String(r.get('slid')) === '" + safeSlId + "') { slRec = r; return false; }"
                        + "          });"
                        + "        }"
                        + "        if (!slRec) {"
                        + "          store.each(function(r) {"
                        + "            if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "          });"
                        + "        }"
                        + "        if (!slRec) {"
                        + "          var nextStart = pageStart + PAGE_SIZE;"
                        + "          if (nextStart < store.getTotalCount()) {"
                        + "            console.log('SL not on this page, trying next: start=' + nextStart);"
                        + "            loadAndSearch(nextStart);"
                        + "          } else {"
                        + "            console.error('SL not found after all pages: " + slName + " | totalCount=' + store.getTotalCount());"
                        + "            window.__slSearchTotalCount = store.getTotalCount();"
                        + "            window.__slSearchDone = true; window.__slSearchFound = false;"
                        + "          }"
                        + "          return;"
                        + "        }"
                        + "        window.__slRecData = JSON.stringify(slRec.getData());"
                        + "        combo.select(slRec);"
                        + "        combo.fireEvent('select', combo, slRec);"
                        + "        combo.blur();"
                        + "        var __glPollCount0 = 0;"
                        + "        var __glPollStart0 = Date.now();"
                        + "        function __pollGlRow0() {"
                        + "          var rec = childGrid.getStore().getAt(0);"
                        + "          if (!rec) { window.__slSearchDone = true; window.__slSearchFound = false; return; }"
                        + "          rec.set('ledgerCode', slRec.get('glCode') || rec.get('ledgerCode') || 0);"
                        + "          rec.set('glCode',     slRec.get('glCode') || rec.get('ledgerCode') || 0);"
                        + "          var glName  = slRec.get('glName');"
                        + "          var glGrp   = slRec.get('generalLedgerGroupName');"
                        + "          var glAbbre = slRec.get('generalLedgerAbbre');"
                        + "          if (glName)  rec.set('ledgerName',             glName);"
                        + "          if (glGrp) { rec.set('ledgerGroupName',        glGrp);"
                        + "                       rec.set('generalLedgerGroupName', glGrp); }"
                        + "          if (glAbbre) rec.set('ledgerAbbreviation',     glAbbre);"
                        + "          var rawTdsFlag = slRec.get('tdsApp') || '';"
                        + "          var needTdsInfo = rawTdsFlag === 'Y' || rawTdsFlag.toUpperCase() === 'YES';"
                        + "          var tdsInfoOk = !needTdsInfo || rec.get('tdsInfo') != null;"
                        + "          var glReady = rec.get('ledgerCode') > 0 && (rec.get('generalLedgerGroupName') || '') !== '' && !Ext.Ajax.isLoading() && tdsInfoOk;"
                        + "          if (!glReady && __glPollCount0 < 30) { __glPollCount0++; setTimeout(__pollGlRow0, 400); return; }"
                        + "          rec.set('slCode',    slRec.get('slCode'));"
                        + "          rec.set('slName',    slRec.get('slName'));"
                        + "          rec.set('slid',      slRec.get('slid'));"
                        + "          rec.set('className', slRec.get('className'));"
                        + "          var tdsRaw = slRec.get('tdsApp');"
                        + "          if (tdsRaw != null && tdsRaw !== '') {"
                        + "            var normTds = (tdsRaw === 'Y' || tdsRaw.toUpperCase() === 'YES') ? 'Yes' : 'No';"
                        + "            rec.set('tdsApp',    tdsRaw);"   // preserve raw server flag (e.g. 'YES') so the app renders 'Calc. Amount'
                        + "            rec.set('TDSdeduct', normTds);"
                        + "            rec.set('deductTDS', normTds);"
                        + "          }"
                        + "          window.__gridRecGl = 'ledgerName=' + (rec.get('ledgerName') || 'EMPTY')"
                        + "            + ' | ledgerGroupName=' + (rec.get('ledgerGroupName') || 'EMPTY')"
                        + "            + ' | ledgerCode=' + rec.get('ledgerCode') + ' | slCode=' + rec.get('slCode') + ' | polls=' + __glPollCount0 + ' | ms=' + (Date.now() - __glPollStart0);"
                        + "          window.__slSearchDiag = { lastLedgerCode: rec.get('ledgerCode'), lastLedgerName: rec.get('ledgerName'), lastLedgerGroupName: rec.get('ledgerGroupName'), lastAjaxLoading: Ext.Ajax.isLoading(), polls: __glPollCount0, ms: (Date.now() - __glPollStart0) };"
                        + "          window.__capturedGlRows = window.__capturedGlRows || [];"
                        + "          window.__capturedGlRows[0] = {"
                        + "            name:           rec.get('ledgerName')             || '',"
                        + "            groupName:      rec.get('ledgerGroupName')        || '',"
                        + "            generalGrpName: rec.get('generalLedgerGroupName') || '',"
                        + "            abbre:          rec.get('ledgerAbbreviation')     || '',"
                        + "            code:           rec.get('ledgerCode')             || 0,"
                        + "            glCode:         rec.get('glCode')                 || 0,"
                        + "            slCode:         rec.get('slCode')                 || 0,"
                        + "            slName:         rec.get('slName')                 || '',"
                        + "            slid:           rec.get('slid')                   || '',"
                        + "            className:      rec.get('className')              || '',"
                        + "            tdsApp:         rec.get('tdsApp')                 || '',"
                        + "            TDSdeduct:      rec.get('TDSdeduct')              || '',"
                        + "            deductTDS:      rec.get('deductTDS')              || ''"
                        + "          };"
                        + "          childGrid.getView().refreshNode(0);"
                        + "          window.__fullGridRec0 = JSON.stringify(rec.getData());"
                        + "          console.log('[CAPTURED] slCode=' + rec.get('slCode') + ' ledgerCode=' + rec.get('ledgerCode') + ' ledgerGroupName=' + rec.get('ledgerGroupName') + ' polls=' + __glPollCount0);"
                        + "          console.log('FULL GRID RECORD: ' + JSON.stringify(rec.getData()));"
                        + "          window.__slSearchDone = true; window.__slSearchFound = true;"
                        + "        }"
                        + "        setTimeout(__pollGlRow0, 600);"
                        + "      }"
                        + "    });"
                        + "  }"
                        + "  loadAndSearch(0);"
                        + "} catch(e) { console.error('select ERROR: ' + e.message); window.__slSearchDone = true; window.__slSearchFound = false; throw e; }";

        ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("=== JS page-walk started for SL '" + slName + "' ===");

        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Boolean done = (Boolean) ((JavascriptExecutor) driver)
                    .executeScript("return window.__slSearchDone === true;");
            if (Boolean.TRUE.equals(done)) break;
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        Boolean pageWalkFound = (Boolean) ((JavascriptExecutor) driver)
                .executeScript("return window.__slSearchFound === true;");
        Long slSearchTotalCount = (Long) ((JavascriptExecutor) driver)
                .executeScript("return window.__slSearchTotalCount !== undefined ? window.__slSearchTotalCount : -1;");
        System.out.println("=== Page-walk complete for SL '" + slName + "' | found=" + pageWalkFound
                + (Boolean.FALSE.equals(pageWalkFound) ? " | storeTotal=" + slSearchTotalCount : "") + " ===");
        printSlDebug(driver);

        String verifyResult = verifySelectedSubLedgerRecord(driver, 0, gridItemId);
        assertSubLedgerSelected(mode, slName, safeSlId, verifyResult, 0, Boolean.TRUE.equals(pageWalkFound));
        String safeRefSite = (referenceSite == null) ? "" : referenceSite;
        if (!safeRefSite.isEmpty()) {
            ExtentLogger.info("[SL SELECT] ReferenceSite will be set via setSubLedgerReferenceSite | Row=0 | Value=" + safeRefSite);
            setSubLedgerReferenceSite(driver, 0, safeRefSite, gridItemId);
        } else {
            ExtentLogger.info("[SL SELECT] ReferenceSite not provided, no Reference Site change | Row=0");
        }
        System.out.println("=== END: setSubLedger ===");
    }

    /**
     * Reads all window debug variables set inside the GL poll and logs them to both
     * the system console and the ExtentReport for diagnosis:
     *   __slRecData      — full JSON of the slCode combo record (what the server sent for this SL)
     *   __fullGridRec0   — full JSON of grid row-0 record after GL poll completes (all fields)
     *   __gridRecGl      — concise GL summary (ledgerName/group/code/polls)
     *   __slSearchDiag   — structured poll diagnostics object
     *   capturedTds      — tdsApp/TDSdeduct/deductTDS as captured in __capturedGlRows[0]
     */
    private static void printSlDebug(WebDriver driver) {
        String slRec = (String) ((JavascriptExecutor) driver)
                .executeScript("return window.__slRecData !== undefined ? window.__slRecData : 'NOT_CAPTURED';");
        String gridGl = (String) ((JavascriptExecutor) driver)
                .executeScript("return window.__gridRecGl !== undefined ? window.__gridRecGl : 'NOT_CAPTURED';");
        String diagStr = (String) ((JavascriptExecutor) driver)
                .executeScript("return window.__slSearchDiag !== undefined ? JSON.stringify(window.__slSearchDiag) : 'NOT_CAPTURED';");
        String fullRec = (String) ((JavascriptExecutor) driver)
                .executeScript("return window.__fullGridRec0 !== undefined ? window.__fullGridRec0 : 'NOT_CAPTURED';");
        String capturedTds = (String) ((JavascriptExecutor) driver)
                .executeScript(
                        "var c = window.__capturedGlRows && window.__capturedGlRows[0];" +
                        "return c ? JSON.stringify({tdsApp: c.tdsApp, TDSdeduct: c.TDSdeduct, deductTDS: c.deductTDS}) : 'NO_CAPTURE';");

        System.out.println("=== [SL_COMBO_RECORD] "         + slRec      + " ===");
        System.out.println("=== [FULL_GRID_REC_AFTER_POLL] " + fullRec    + " ===");
        System.out.println("=== [CAPTURED_TDS] "             + capturedTds + " ===");
        System.out.println("=== [GL_AFTER_FIRE_EVENT] "      + gridGl     + " ===");
        System.out.println("=== [GL_POLL_DIAG] "             + diagStr    + " ===");

        ExtentLogger.info("[SL_COMBO_RECORD] "         + slRec);
        ExtentLogger.info("[FULL_GRID_REC_AFTER_POLL] " + fullRec);
        ExtentLogger.info("[CAPTURED_TDS] "             + capturedTds);
        ExtentLogger.info("[GL_AFTER_FIRE_EVENT] "      + gridGl);
        ExtentLogger.info("[GL_POLL_DIAG] "             + diagStr);
    }

    public static void restoreGlFieldsFromCapture(WebDriver driver, int rowIndex, String gridItemId) {
        String script =
                "try {" +
                "  var cap = window.__capturedGlRows && window.__capturedGlRows[" + rowIndex + "];" +
                "  if (!cap) return 'NO_CAPTURE';" +
                "  var g = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];" +
                "  if (!g) return 'GRID_NOT_FOUND';" +
                "  var rec = g.getStore().getAt(" + rowIndex + ");" +
                "  if (!rec) return 'REC_NOT_FOUND';" +
                "  if (cap.name)      rec.set('ledgerName',             cap.name);" +
                "  if (cap.groupName) rec.set('ledgerGroupName',        cap.groupName);" +
                "  var gg = cap.generalGrpName || cap.groupName || '';" +
                "  if (gg)            rec.set('generalLedgerGroupName', gg);" +
                "  if (cap.abbre)     rec.set('ledgerAbbreviation',     cap.abbre);" +
                "  rec.set('ledgerCode', cap.code);" +
                "  rec.set('glCode', cap.glCode || cap.code || 0);" +
                "  if (cap.slCode)    rec.set('slCode',    cap.slCode);" +
                "  if (cap.slName)    rec.set('slName',    cap.slName);" +
                "  if (cap.slid)      rec.set('slid',      cap.slid);" +
                "  if (cap.className) rec.set('className', cap.className);" +
                "  g.getView().refreshNode(" + rowIndex + ");" +
                "  return 'RESTORED: ' + rec.get('ledgerName') + ' | ' + rec.get('ledgerGroupName')" +
                "         + ' | slCode=' + rec.get('slCode') + ' | slid=' + rec.get('slid');" +
                "} catch(e) { return 'ERROR: ' + e.message; }";
        Object result = ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("=== [GL_RESTORE] row=" + rowIndex + " => " + result + " ===");
    }

    public static void restoreAllGlFieldsFromCapture(WebDriver driver, String gridItemId) {
        String script =
                "try {" +
                "  if (!window.__capturedGlRows) return 'NO_CAPTURE';" +
                "  var g = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];" +
                "  if (!g) return 'GRID_NOT_FOUND';" +
                "  var store = g.getStore(); var view = g.getView(); var results = [];" +
                "  for (var i = 0; i < window.__capturedGlRows.length; i++) {" +
                "    var cap = window.__capturedGlRows[i];" +
                "    if (!cap) continue;" +
                "    var rec = store.getAt(i); if (!rec) continue;" +
                "    rec.set('ledgerCode',             cap.code      || 0);" +
                "    rec.set('glCode',                 cap.glCode || cap.code || 0);" +
                "    rec.set('ledgerName',             cap.name      || '');" +
                "    rec.set('glName',                 cap.name      || '');" +
                "    rec.set('ledgerGroupName',        cap.groupName || '');" +
                "    var gg = cap.generalGrpName || cap.groupName || '';" +
                "    rec.set('generalLedgerGroupName', gg);" +
                "    rec.set('ledgerAbbreviation',     cap.abbre     || '');" +
                "    rec.set('generalLedgerAbbre',     cap.abbre     || '');" +
                "    rec.set('tdsApp',                 cap.tdsApp    || '');" +
                "    rec.set('TDSdeduct',              cap.TDSdeduct || '');" +
                "    rec.set('deductTDS',              cap.deductTDS || '');" +
                "    if (view) view.refreshNode(i);" +
                "    results.push('row=' + i + ' code=' + rec.get('ledgerCode') + ' glCode=' + rec.get('glCode') + ' grp=' + gg + ' tds=' + (rec.get('tdsApp') || ''));" +
                "  }" +
                "  return 'PRE_SAVE_GL: ' + results.join('; ');" +
                "} catch(e) { return 'ERROR: ' + e.message; }";
        Object result = ((JavascriptExecutor) driver).executeScript(script);
        ExtentLogger.info("[GL_PRE_SAVE_RESTORE] " + result);
    }

    /**
     * Override the "Deduct TDS" value on a sub-ledger grid row from test data.
     *
     * <p>Behaviour:</p>
     * <ol>
     *   <li>If the grid cell already shows the requested value (after vendor selection), skip — no edit needed.</li>
     *   <li>Otherwise open the inline cell editor via the ExtJS {@code editingPlugin.startEdit()} —
     *       equivalent to a double-click — then set the Yes/No combo value and complete the edit.</li>
     * </ol>
     *
     * <p>The corresponding entry in {@code window.__capturedGlRows[rowIndex]} is also updated so the
     * pre-save restore in {@link #restoreAllGlFieldsFromCapture(WebDriver, String)} preserves the
     * override instead of clobbering it.</p>
     */
    public static void setDeductTdsForRow(WebDriver driver, int rowIndex, String value, String gridItemId) {
        if (value == null || value.trim().isEmpty()) return;
        String safeValue = value.trim();
        String script =
                "try {" +
                // ── Phase 1: locate grid and record ──────────────────────────────────
                "  var g = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];" +
                "  if (!g) return 'GRID_NOT_FOUND';" +
                "  var rec = g.getStore().getAt(" + rowIndex + ");" +
                "  if (!rec) return 'REC_NOT_FOUND';" +
                // ── Phase 2: skip if already the right value ──────────────────────────
                "  var cur = rec.get('TDSdeduct') || rec.get('deductTDS') || '';" +
                "  var norm = (cur === 'Y' || cur.toUpperCase() === 'YES') ? 'Yes' : (cur ? 'No' : '');" +
                "  if (norm === '" + safeValue + "') {" +
                "    return 'ALREADY_SET: row=" + rowIndex + " value=" + safeValue + "';" +
                "  }" +
                // ── Phase 3: find the TDSdeduct/deductTDS column ─────────────────────
                "  var cols = g.getColumns();" +
                "  var tdsCol = null;" +
                "  for (var ci = 0; ci < cols.length; ci++) {" +
                "    var di = cols[ci].dataIndex || '';" +
                "    if (di === 'TDSdeduct' || di === 'deductTDS') { tdsCol = cols[ci]; break; }" +
                "  }" +
                "  if (!tdsCol) return 'COL_NOT_FOUND: TDSdeduct/deductTDS';" +
                // ── Phase 4: open inline editor (equiv. double-click) ─────────────────
                "  var plugin = g.editingPlugin;" +
                "  if (!plugin) return 'EDITING_PLUGIN_NOT_FOUND';" +
                "  plugin.startEdit(rec, tdsCol);" +
                "  var field = plugin.activeEditor && plugin.activeEditor.field;" +
                "  if (!field) return 'ACTIVE_EDITOR_NOT_FOUND';" +
                "  field.setValue('" + safeValue + "');" +
                "  plugin.completeEdit();" +
                // ── Phase 5: update capture cache and refresh row ─────────────────────
                "  if (window.__capturedGlRows && window.__capturedGlRows[" + rowIndex + "]) {" +
                "    window.__capturedGlRows[" + rowIndex + "].TDSdeduct = '" + safeValue + "';" +
                "    window.__capturedGlRows[" + rowIndex + "].deductTDS = '" + safeValue + "';" +
                "  }" +
                "  g.getView().refreshNode(rec);" +
                "  return 'APPLIED: row=" + rowIndex + " value=" + safeValue + "';" +
                "} catch(e) { return 'ERROR: ' + e.message; }";
        Object result = ((JavascriptExecutor) driver).executeScript(script);
        String resultStr = String.valueOf(result);
        if (resultStr.startsWith("ALREADY_SET") || resultStr.startsWith("APPLIED")) {
            ExtentLogger.info("[DEDUCT_TDS] " + resultStr);
        } else {
            ExtentLogger.warning("[DEDUCT_TDS] Failed to apply on row=" + rowIndex
                    + " — " + resultStr + " | requestedValue=" + safeValue);
        }
    }

    private static String verifySelectedSubLedgerRecord(WebDriver driver, int rowIndex) {
        return verifySelectedSubLedgerRecord(driver, rowIndex, "arvch_grid");
    }

    private static String verifySelectedSubLedgerRecord(WebDriver driver, int rowIndex, String gridItemId) {
        String script =
                "try {" +
                "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];" +
                "  if (!childGrid) return 'ERROR: " + gridItemId + " not found';" +
                "  var rec = childGrid.getStore().getAt(" + rowIndex + ");" +
                "  if (!rec) return 'ERROR: record not found at row " + rowIndex + "';" +
                "  return rec.get('slName') + '|' + rec.get('slid') + '|' + rec.get('slCode')" +
                "       + '|' + (rec.get('className') || '') + '|' + (rec.get('ledgerName') || '');" +
                "} catch(e) { return 'ERROR: ' + e.message; }";
        return (String) ((JavascriptExecutor) driver).executeScript(script);
    }

    private static void assertSubLedgerSelected(String mode, String expectedSlName, String slId,
                                                String verifyResult, int rowIndex, boolean pageWalkFound) {
        if (!pageWalkFound) {
            throw new SubLedgerNotFoundException(expectedSlName, rowIndex, "");
        }
        if (verifyResult == null || verifyResult.startsWith("ERROR")) {
            throw new SubLedgerNotFoundException(expectedSlName, rowIndex,
                    "JS verification error: " + verifyResult);
        }
        String[] parts = verifyResult.split("\\|", -1);
        String actualSlName     = parts.length > 0 ? parts[0] : "";
        String actualSlId       = parts.length > 1 ? parts[1] : "";
        String actualSlCode     = parts.length > 2 ? parts[2] : "";
        String actualClassName  = parts.length > 3 ? parts[3] : "";
        String actualLedgerName = parts.length > 4 ? parts[4] : "";

        if (actualSlName == null || actualSlName.isBlank()) {
            throw new SubLedgerNotFoundException(expectedSlName, rowIndex, "");
        }
        if (!actualSlName.equals(expectedSlName)) {
            throw new SubLedgerNotFoundException(expectedSlName, rowIndex, actualSlName);
        }

        String base = " | Row=" + rowIndex
                + " | SlName="  + actualSlName
                + " | SlId="    + actualSlId
                + " | SlCode="  + actualSlCode
                + " | SlType="  + actualClassName
                + " | GL="      + actualLedgerName;

        if ("NAME_ONLY".equals(mode)) {
            ReportLogger.info("[SL SELECT] Mode=NAME_ONLY" + base
                    + " | Reason=Id not provided in Excel, matched by name alone");
        } else {
            if (actualSlId.equals(slId)) {
                ReportLogger.info("[SL SELECT] Mode=NAME_AND_ID" + base
                        + " | ExcelId=" + slId + " | Reason=Matched by name and id");
            } else {
                ReportLogger.info("[SL SELECT] Mode=NAME_AND_ID | WARNING=Id match failed, fell back to name-only"
                        + base + " | ExcelId=" + slId);
            }
        }
    }

    /**
     * Adds a new empty row to the arvch_grid subledger store.
     * Call this before setSubLedgerForRow() for any row index > 0.
     */
    public static void addSubLedgerRow(WebDriver driver) {
        addSubLedgerRow(driver, "arvch_grid");
    }

    public static void addSubLedgerRow(WebDriver driver, String gridItemId) {
        String script =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];"
                        + "  if (!childGrid) throw new Error('" + gridItemId + " not found');"
                        + "  var store = childGrid.getStore();"
                        + "  store.add({});"
                        + "  console.log('Added new subledger row, total=' + store.getCount());"
                        + "  return 'SUCCESS: rows=' + store.getCount();"
                        + "} catch(e) { return 'ERROR: ' + e.message; }";
        Object result = ((JavascriptExecutor) driver).executeScript(script);
        System.out.println("addSubLedgerRow result: " + result);
        try { Thread.sleep(500); } catch (InterruptedException e) { e.printStackTrace(); }
    }

    /**
     * Sets the subledger name on a specific row (rowIndex) in the specified grid.
     * For row 0 use setSubLedger(); for subsequent rows use this method after addSubLedgerRow().
     */
    public static void setSubLedgerForRow(WebDriver driver, int rowIndex, String slName, String slId, String referenceSite) {
        setSubLedgerForRow(driver, rowIndex, slName, slId, referenceSite, "arvch_grid");
    }

    public static void setSubLedgerForRow(WebDriver driver, int rowIndex, String slName, String slId, String referenceSite, String gridItemId) {
        slName = (slName != null) ? slName.trim() : "";
        slId   = (slId   != null) ? slId.trim()   : "";
        if (slName.isEmpty()) {
            throw new IllegalArgumentException(
                "setSubLedgerForRow called with null/empty slName at rowIndex=" + rowIndex
                + " (slId='" + slId + "'). "
                + "Check that the data source (Excel or DB) is providing a non-empty Sub Ledger name."
            );
        }
        System.out.println("=== START: setSubLedgerForRow row=" + rowIndex + " sl='" + slName + "' id='" + slId + "' ===");
        String mode = slId.isEmpty() ? "NAME_ONLY" : "NAME_AND_ID";
        String safeSlId = slId;

        String clickScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];"
                        + "  if (!childGrid) throw new Error('" + gridItemId + " not found');"
                        + "  var view = childGrid.getView();"
                        + "  var rowEl = view.getNode(" + rowIndex + ");"
                        + "  if (!rowEl) throw new Error('child grid row " + rowIndex + " DOM node not found');"
                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var slColIndex = -1;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'slCode') { slColIndex = i; break; }"
                        + "  }"
                        + "  if (slColIndex < 0) throw new Error('slCode column not found');"
                        + "  var cells = rowEl.querySelectorAll('td.x-grid-cell');"
                        + "  if (cells.length <= slColIndex) throw new Error('Not enough cells, found: ' + cells.length);"
                        + "  cells[slColIndex].click();"
                        + "  return 'CLICKED';"
                        + "} catch(e) { console.error('click ERROR: ' + e.message); throw e; }";

        Object clickResult = ((JavascriptExecutor) driver).executeScript(clickScript);
        System.out.println("=== Cell click result: " + clickResult + " ===");

        try { Thread.sleep(600); } catch (InterruptedException e) { e.printStackTrace(); }

        String selectScript =
                "window.__slSearchDone = false; window.__slSearchFound = false;"
                        + "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];"
                        + "  var combo = Ext.ComponentQuery.query('combo[name=slCode]')[0];"
                        + "  if (!combo) throw new Error('slCode combo not found after cell click');"
                        + "  var PAGE_SIZE = 25;"
                        + "  var targetRow = " + rowIndex + ";"
                        + "  function loadAndSearch(pageStart) {"
                        + "    combo.getStore().load({"
                        + "      params: { queryFieldName: ['slName','className'], query: '" + slName + "', page: Math.floor(pageStart / PAGE_SIZE) + 1, start: pageStart, limit: PAGE_SIZE },"
                        + "      callback: function() {"
                        + "        var store = combo.getStore();"
                        + "        console.log('Row ' + targetRow + ' page walk: page=' + (Math.floor(pageStart / PAGE_SIZE) + 1) + ' start=' + pageStart + ' loaded=' + store.getCount() + ' total=' + store.getTotalCount());"
                        + "        var slRec = null;"
                        + "        if ('" + safeSlId + "' !== '') {"
                        + "          store.each(function(r) {"
                        + "            if (r.get('slName') === '" + slName + "' && String(r.get('slid')) === '" + safeSlId + "') { slRec = r; return false; }"
                        + "          });"
                        + "        }"
                        + "        if (!slRec) {"
                        + "          store.each(function(r) {"
                        + "            if (r.get('slName') === '" + slName + "') { slRec = r; return false; }"
                        + "          });"
                        + "        }"
                        + "        if (!slRec) {"
                        + "          var nextStart = pageStart + PAGE_SIZE;"
                        + "          if (nextStart < store.getTotalCount()) {"
                        + "            console.log('SL not on this page, trying next: start=' + nextStart);"
                        + "            loadAndSearch(nextStart);"
                        + "          } else {"
                        + "            console.error('SL not found after all pages: " + slName + " | totalCount=' + store.getTotalCount());"
                        + "            window.__slSearchTotalCount = store.getTotalCount();"
                        + "            window.__slSearchDone = true; window.__slSearchFound = false;"
                        + "          }"
                        + "          return;"
                        + "        }"
                        + "        window.__slRecData = JSON.stringify(slRec.getData());"
                        + "        combo.select(slRec);"
                        + "        combo.fireEvent('select', combo, slRec);"
                        + "        combo.blur();"
                        + "        var __glPollCountR = 0;"
                        + "        var __glPollStartR = Date.now();"
                        + "        function __pollGlRowR() {"
                        + "          var rec = childGrid.getStore().getAt(targetRow);"
                        + "          if (!rec) { window.__slSearchDone = true; window.__slSearchFound = false; return; }"
                        + "          rec.set('ledgerCode', slRec.get('glCode') || rec.get('ledgerCode') || 0);"
                        + "          rec.set('glCode',     slRec.get('glCode') || rec.get('ledgerCode') || 0);"
                        + "          var glName  = slRec.get('glName');"
                        + "          var glGrp   = slRec.get('generalLedgerGroupName');"
                        + "          var glAbbre = slRec.get('generalLedgerAbbre');"
                        + "          if (glName)  rec.set('ledgerName',             glName);"
                        + "          if (glGrp) { rec.set('ledgerGroupName',        glGrp);"
                        + "                       rec.set('generalLedgerGroupName', glGrp); }"
                        + "          if (glAbbre) rec.set('ledgerAbbreviation',     glAbbre);"
                        + "          var rawTdsFlag = slRec.get('tdsApp') || '';"
                        + "          var needTdsInfo = rawTdsFlag === 'Y' || rawTdsFlag.toUpperCase() === 'YES';"
                        + "          var tdsInfoOk = !needTdsInfo || rec.get('tdsInfo') != null;"
                        + "          var glReady = rec.get('ledgerCode') > 0 && (rec.get('generalLedgerGroupName') || '') !== '' && !Ext.Ajax.isLoading() && tdsInfoOk;"
                        + "          if (!glReady && __glPollCountR < 30) { __glPollCountR++; setTimeout(__pollGlRowR, 400); return; }"
                        + "          rec.set('slCode',    slRec.get('slCode'));"
                        + "          rec.set('slName',    slRec.get('slName'));"
                        + "          rec.set('slid',      slRec.get('slid'));"
                        + "          rec.set('className', slRec.get('className'));"
                        + "          var tdsRaw = slRec.get('tdsApp');"
                        + "          if (tdsRaw != null && tdsRaw !== '') {"
                        + "            var normTds = (tdsRaw === 'Y' || tdsRaw.toUpperCase() === 'YES') ? 'Yes' : 'No';"
                        + "            rec.set('tdsApp',    tdsRaw);"   // preserve raw server flag (e.g. 'YES') so the app renders 'Calc. Amount'
                        + "            rec.set('TDSdeduct', normTds);"
                        + "            rec.set('deductTDS', normTds);"
                        + "          }"
                        + "          window.__gridRecGl = 'row=' + targetRow + ' | ledgerName=' + (rec.get('ledgerName') || 'EMPTY')"
                        + "            + ' | ledgerGroupName=' + (rec.get('ledgerGroupName') || 'EMPTY')"
                        + "            + ' | ledgerCode=' + rec.get('ledgerCode') + ' | slCode=' + rec.get('slCode') + ' | polls=' + __glPollCountR + ' | ms=' + (Date.now() - __glPollStartR);"
                        + "          window.__slSearchDiag = { lastLedgerCode: rec.get('ledgerCode'), lastLedgerName: rec.get('ledgerName'), lastLedgerGroupName: rec.get('ledgerGroupName'), lastAjaxLoading: Ext.Ajax.isLoading(), polls: __glPollCountR, ms: (Date.now() - __glPollStartR) };"
                        + "          window.__capturedGlRows = window.__capturedGlRows || [];"
                        + "          window.__capturedGlRows[targetRow] = {"
                        + "            name:           rec.get('ledgerName')             || '',"
                        + "            groupName:      rec.get('ledgerGroupName')        || '',"
                        + "            generalGrpName: rec.get('generalLedgerGroupName') || '',"
                        + "            abbre:          rec.get('ledgerAbbreviation')     || '',"
                        + "            code:           rec.get('ledgerCode')             || 0,"
                        + "            glCode:         rec.get('glCode')                 || 0,"
                        + "            slCode:         rec.get('slCode')                 || 0,"
                        + "            slName:         rec.get('slName')                 || '',"
                        + "            slid:           rec.get('slid')                   || '',"
                        + "            className:      rec.get('className')              || '',"
                        + "            tdsApp:         rec.get('tdsApp')                 || '',"
                        + "            TDSdeduct:      rec.get('TDSdeduct')              || '',"
                        + "            deductTDS:      rec.get('deductTDS')              || ''"
                        + "          };"
                        + "          childGrid.getView().refreshNode(targetRow);"
                        + "          window.__fullGridRecN = window.__fullGridRecN || {};"
                        + "          window.__fullGridRecN[targetRow] = JSON.stringify(rec.getData());"
                        + "          console.log('[CAPTURED] row=' + targetRow + ' slCode=' + rec.get('slCode') + ' ledgerCode=' + rec.get('ledgerCode') + ' polls=' + __glPollCountR);"
                        + "          window.__slSearchDone = true; window.__slSearchFound = true;"
                        + "        }"
                        + "        setTimeout(__pollGlRowR, 600);"
                        + "      }"
                        + "    });"
                        + "  }"
                        + "  loadAndSearch(0);"
                        + "} catch(e) { console.error('select ERROR: ' + e.message); window.__slSearchDone = true; window.__slSearchFound = false; throw e; }";

        ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("=== JS page-walk started for row=" + rowIndex + " SL='" + slName + "' ===");

        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            Boolean done = (Boolean) ((JavascriptExecutor) driver)
                    .executeScript("return window.__slSearchDone === true;");
            if (Boolean.TRUE.equals(done)) break;
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        Boolean pageWalkFound = (Boolean) ((JavascriptExecutor) driver)
                .executeScript("return window.__slSearchFound === true;");
        Long slSearchTotalCount = (Long) ((JavascriptExecutor) driver)
                .executeScript("return window.__slSearchTotalCount !== undefined ? window.__slSearchTotalCount : -1;");
        System.out.println("=== Page-walk complete for row=" + rowIndex + " SL='" + slName + "' | found=" + pageWalkFound
                + (Boolean.FALSE.equals(pageWalkFound) ? " | storeTotal=" + slSearchTotalCount : "") + " ===");
        printSlDebug(driver);

        String verifyResult = verifySelectedSubLedgerRecord(driver, rowIndex, gridItemId);
        assertSubLedgerSelected(mode, slName, safeSlId, verifyResult, rowIndex, Boolean.TRUE.equals(pageWalkFound));
        String safeRefSite = (referenceSite == null) ? "" : referenceSite;
        if (!safeRefSite.isEmpty()) {
            ExtentLogger.info("[SL SELECT] ReferenceSite will be set via setSubLedgerReferenceSite | Row=" + rowIndex + " | Value=" + safeRefSite);
            setSubLedgerReferenceSite(driver, rowIndex, safeRefSite, gridItemId);
        } else {
            ExtentLogger.info("[SL SELECT] ReferenceSite not provided, no Reference Site change | Row=" + rowIndex);
        }
        System.out.println("=== END: setSubLedgerForRow ===");
    }

    public static void setSubLedgerReferenceSite(WebDriver driver, int rowIndex, String referenceSiteName) {
        setSubLedgerReferenceSite(driver, rowIndex, referenceSiteName, "arvch_grid");
    }

    public static void setSubLedgerReferenceSite(WebDriver driver, int rowIndex, String referenceSiteName, String gridItemId) {
        System.out.println("=== START: setSubLedgerReferenceSite row=" + rowIndex + " site='" + referenceSiteName + "' ===");

        // ── setSubLedgerReferenceSite — JavaScript Strategy ──────────────────────────
        // 1. plugin.startEdit() opens the cell editor so the app configures the combo's
        //    store with SL-specific extraParams (filtered to only valid sites for this SL).
        // 2. We capture those extraParams BEFORE cancelling, then use them in our own load.
        //    This prevents loading ALL reference sites globally and accidentally picking a
        //    site that is not valid for the current SL — which causes the server error
        //    "ReferenceSiteCode doesn't exist".
        // 3. plugin.cancelEdit() is called SYNCHRONOUSLY, ensuring the app's 'canceledit'
        //    event handler (and any setTimeout(fn,0) it schedules) fires and completes
        //    BEFORE the async AJAX callback that calls rec.set(). This prevents the app
        //    from clearing the value we write.
        // 4. When target is blank, we use store.getAt(0) from the SL-filtered load.
        // 5. When target is named, we use two-pass matching (name first, then secondary
        //    fields) to avoid false positives (e.g. picking "AFFIFA 1" whose ouName is
        //    "Hena Embroidery" before the actual "Hena Embroidery" record).
        // 6. If the store is empty on first load (first-ever access to this column), a
        //    single automatic retry fires after 800 ms.
        // ─────────────────────────────────────────────────────────────────────────────────
        String selectScript =
                "try {"
                        + "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];"
                        + "  if (!childGrid) throw new Error('" + gridItemId + " not found');"

                        + "  var cols = childGrid.getColumnManager().getColumns();"
                        + "  var refSiteCol = null;"
                        + "  for (var i=0; i<cols.length; i++) {"
                        + "    if (cols[i].dataIndex === 'referenceSiteCode') { refSiteCol = cols[i]; break; }"
                        + "  }"
                        + "  if (!refSiteCol) throw new Error('referenceSiteCode column not found');"

                        + "  var plugin = childGrid.editingPlugin;"
                        + "  if (!plugin) throw new Error('editingPlugin not found on " + gridItemId + "');"

                        // Open the editor so the app configures the store with SL-specific params.
                        // Pass the record object instead of the integer rowIndex to avoid the
                        // ExtJS falsy-zero bug: startEdit(0, col) treats 0 as falsy and silently
                        // skips the edit, leaving row 0's Reference Site combo unopened.
                        + "  var gridRec = childGrid.getStore().getAt(" + rowIndex + ");"
                        + "  if (!gridRec) throw new Error('No grid record at row " + rowIndex + "');"
                        + "  plugin.startEdit(gridRec, refSiteCol);"
                        + "  var siteCombo = plugin.activeEditor && plugin.activeEditor.field;"
                        + "  if (!siteCombo) throw new Error('Reference Site combo not active after startEdit');"
                        + "  console.log('RefSite combo found, name=' + siteCombo.name);"

                        // Get the store reference from the active combo.
                        + "  var store = siteCombo.getStore();"

                        + "  window.__refSiteDebug = { status:'loading', storeCount:0, allRecords:[], matchedName:'', matchedCode:0, error:'' };"
                        + "  var __target = '" + referenceSiteName + "';"
                        + "  var __rowIndex = " + rowIndex + ";"
                        + "  var __retried = false;"

                        // ── __applyRefSite: matching + rec.set + refreshNode ──────────────────────
                        + "  function __applyRefSite(s) {"
                        + "    var allRecs = [];"
                        + "    s.each(function(r) { allRecs.push(JSON.stringify(r.getData())); });"
                        + "    window.__refSiteDebug.storeCount = s.getCount();"
                        + "    window.__refSiteDebug.allRecords = allRecs;"
                        + "    var siteRec = null;"

                        // Case 1 — blank target: pick first available site.
                        + "    if (!__target || __target.trim() === '') {"
                        + "      if (s.getCount() > 0) { siteRec = s.getAt(0); window.__refSiteDebug.status = 'AUTO_FIRST_AVAILABLE'; }"
                        // Case 2 — named target: two-pass match (name fields first, then secondary).
                        + "    } else {"
                        // Pass A: primary identity fields only (siteName, name).
                        + "      s.each(function(r) {"
                        + "        var pf = [r.get('siteName'), r.get('name')];"
                        + "        for (var fi=0; fi<pf.length; fi++) {"
                        + "          if (pf[fi] && String(pf[fi]).toLowerCase() === __target.toLowerCase()) { siteRec = r; return false; }"
                        + "        }"
                        + "      });"
                        // Pass B: secondary fields only if Pass A found nothing.
                        + "      if (!siteRec) {"
                        + "        s.each(function(r) {"
                        + "          var sf = [r.get('ouName'), r.get('siteCode'), r.get('code'), r.get('shortName')];"
                        + "          for (var fi=0; fi<sf.length; fi++) {"
                        + "            if (sf[fi] && String(sf[fi]).toLowerCase() === __target.toLowerCase()) { siteRec = r; return false; }"
                        + "          }"
                        + "        });"
                        + "      }"
                        + "      if (!siteRec && s.getCount() > 0) { window.__refSiteDebug.status = 'FALLBACK_FIRST_NO_MATCH'; siteRec = s.getAt(0); }"
                        + "    }"

                        + "    if (!siteRec) { window.__refSiteDebug.status = 'NO_RECORD_EMPTY_STORE'; return; }"

                        + "    var rec = childGrid.getStore().getAt(__rowIndex);"
                        + "    if (!rec) { window.__refSiteDebug.status = 'ERROR_NO_GRID_REC'; return; }"
                        + "    var refSiteCode = siteRec.get('code') || siteRec.get('siteCode') || siteRec.get('id') || 0;"
                        + "    var refSiteName = siteRec.get('siteName') || siteRec.get('name') || siteRec.get('ouName') || '';"
                        + "    var refSiteType   = siteRec.get('siteType') || siteRec.get('type') || 'OU';"
                        + "    var refSiteOUName = siteRec.get('ouName') || '';"
                        + "    rec.set('referenceSiteCode',   refSiteCode);"
                        + "    rec.set('referenceSiteName',   refSiteName);"
                        + "    rec.set('referenceSiteType',   refSiteType);"
                        + "    rec.set('referenceSiteOUName', refSiteOUName);"
                        // Pass the record object (not integer) to refreshNode — avoids the falsy-0
                        // bug in some ExtJS versions that silently skips row 0 when given 0.
                        + "    childGrid.getView().refreshNode(childGrid.getStore().getAt(__rowIndex));"
                        + "    window.__refSiteDebug.status      = 'DONE';"
                        + "    window.__refSiteDebug.matchedName = refSiteName;"
                        + "    window.__refSiteDebug.matchedCode = refSiteCode;"
                        + "  }"  // end __applyRefSite

                        // ── KEY ORDERING CHANGE ───────────────────────────────────────────────────
                        // PROBLEM: calling plugin.cancelEdit() BEFORE store.load() causes the
                        // app's beforeedit-configured proxy URL/params to be reset by canceledit
                        // handlers. On first-ever access to this column (single-row vouchers, or
                        // any row where this editor was never opened before), the proxy has no URL
                        // yet and the load returns 0 records regardless of params.
                        //
                        // FIX: load the store while the editor is still OPEN (proxy URL is active).
                        // Inside the load callback:
                        //   1. plugin.cancelEdit() → app's canceledit handler fires and schedules
                        //      its field-clearing cleanup via setTimeout(cleanup, 0).
                        //   2. setTimeout(fn, 150) → fn fires AFTER cleanup(0) runs.
                        //   3. Inside fn: __applyRefSite() calls rec.set() — this is the final
                        //      write, safely AFTER the app's cleanup has already completed.
                        // ─────────────────────────────────────────────────────────────────────────
                        + "  function __cancelThenApply(s) {"
                        + "    plugin.cancelEdit();"                  // fires canceledit → cleanup scheduled
                        + "    setTimeout(function() { __applyRefSite(s); }, 150);"  // fires AFTER cleanup
                        + "  }"

                        + "  var loadParams = { page:1, start:0, limit:100 };"
                        + "  store.load({"
                        + "    params: loadParams,"
                        + "    callback: function() {"
                        + "      if (store.getCount() === 0 && !__retried) {"
                        + "        __retried = true;"
                        + "        window.__refSiteDebug.status = 'RETRYING';"
                        + "        setTimeout(function() {"
                        + "          store.load({ params: loadParams, callback: function() { __cancelThenApply(store); } });"
                        + "        }, 800);"
                        + "      } else {"
                        + "        __cancelThenApply(store);"
                        + "      }"
                        + "    }"
                        + "  });"
                        + "} catch(e) { if (!window.__refSiteDebug) window.__refSiteDebug = {}; window.__refSiteDebug.error = e.message; throw e; }";

        ((JavascriptExecutor) driver).executeScript(selectScript);
        System.out.println("=== RefSite JS executed for row=" + rowIndex + " site='" + referenceSiteName + "' ===");

        // 4500 ms covers the retry path: 800 ms setTimeout + AJAX round trip inside retry.
        // Normal (no-retry) path completes well within this window.
        try { Thread.sleep(4500); } catch (InterruptedException e) { e.printStackTrace(); }

        // Read debug info written by the async callback back to Java
        String debugScript =
                "var d = window.__refSiteDebug || {};"
                + "return JSON.stringify({"
                + "  status:      d.status      || 'NOT_SET',"
                + "  storeCount:  d.storeCount  || 0,"
                + "  matchedName: d.matchedName || '',"
                + "  matchedCode: d.matchedCode || 0,"
                + "  allRecords:  d.allRecords  || []"
                + "});";
        String debugJson = (String) ((JavascriptExecutor) driver).executeScript(debugScript);
        System.out.println("=== RefSite DEBUG row=" + rowIndex + " target='" + referenceSiteName + "' => " + debugJson + " ===");

        String verifyResult = verifySelectedReferenceSite(driver, rowIndex, gridItemId);
        logReferenceSiteSelection(referenceSiteName, verifyResult, rowIndex);
        restoreGlFieldsFromCapture(driver, rowIndex, gridItemId);
        System.out.println("=== END: setSubLedgerReferenceSite ===");
    }

    private static String verifySelectedReferenceSite(WebDriver driver, int rowIndex) {
        return verifySelectedReferenceSite(driver, rowIndex, "arvch_grid");
    }

    private static String verifySelectedReferenceSite(WebDriver driver, int rowIndex, String gridItemId) {
        String script =
                "try {" +
                "  var childGrid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];" +
                "  if (!childGrid) return 'ERROR: " + gridItemId + " not found';" +
                "  var rec = childGrid.getStore().getAt(" + rowIndex + ");" +
                "  if (!rec) return 'ERROR: record not found at row " + rowIndex + "';" +
                "  return rec.get('referenceSiteName') + '|' + rec.get('referenceSiteCode');" +
                "} catch(e) { return 'ERROR: ' + e.message; }";
        return (String) ((JavascriptExecutor) driver).executeScript(script);
    }

    /**
     * Reads the Reference Site name currently stored in the grid record at the given row.
     * Used after Pass 2 (setSubLedgerReferenceSite) in DB mode to capture the UI-auto-set value
     * so subsequent clickAdjust / clickPaymentButton calls use the correct XPath discriminator.
     */
    public static String getSubLedgerReferenceSiteName(WebDriver driver, int rowIndex) {
        return getSubLedgerReferenceSiteName(driver, rowIndex, "arvch_grid");
    }

    public static String getSubLedgerReferenceSiteName(WebDriver driver, int rowIndex, String gridItemId) {
        String script =
                "try {" +
                "  var grid = Ext.ComponentQuery.query('gridpanel[itemId=" + gridItemId + "]')[0];" +
                "  if (!grid) return '';" +
                "  var rec = grid.getStore().getAt(" + rowIndex + ");" +
                "  if (!rec) return '';" +
                "  return rec.get('referenceSiteName') || '';" +
                "} catch(e) { return ''; }";
        Object result = ((JavascriptExecutor) driver).executeScript(script);
        return result != null ? result.toString() : "";
    }

    private static void logReferenceSiteSelection(String excelValue, String verifyResult, int rowIndex) {
        if (verifyResult == null || verifyResult.startsWith("ERROR")) {
            ExtentLogger.info("[REF_SITE SET] WARNING=Could not verify selected record | Row=" + rowIndex
                    + " | verifyResult=" + verifyResult);
            return;
        }
        String[] parts = verifyResult.split("\\|", -1);
        String actualName = parts.length > 0 ? parts[0] : "";
        String actualCode = parts.length > 1 ? parts[1] : "";

        if (actualName.equals(excelValue)) {
            ExtentLogger.info("[REF_SITE SET] Row=" + rowIndex
                    + " | ReferenceSiteName=" + actualName
                    + " | ReferenceSiteCode=" + actualCode
                    + " | ExcelValue=" + excelValue);
        } else {
            ExtentLogger.info("[REF_SITE SET] WARNING=Name match failed, used first record | Row=" + rowIndex
                    + " | ReferenceSiteName=" + actualName
                    + " | ReferenceSiteCode=" + actualCode
                    + " | ExcelValue=" + excelValue);
        }
    }

    private static long extJsTimeout() {
        long configured = ConfigFactory.getConfig().extjsTimeout();
        long min        = ConfigFactory.getConfig().extjsMinTimeout();
        return Math.max(min, configured);
    }

    /**
     * Opens the Reference Site Name combo editor in the "Ref. Site-wise FIFO Set-Off" popup.
     *
     * <p>Locates the visible FIFO window via ExtJS component query (no fragile DOM id needed),
     * adds a blank record to the store when it is empty, then calls
     * {@code plugin.startEdit(rec, col0)} — the same reliable approach used by
     * {@link #openSubLedgerComboEditor} for the SubLedger grid.</p>
     *
     * @return the combo {@code inputEl.dom.id} that Selenium can type into,
     *         or a string starting with {@code "ERR:"} on failure
     */
    public static String openFifoRefSiteComboEditor(WebDriver driver) {
        String script =
                "try {" +
                "  var wins = Ext.ComponentQuery.query('window');" +
                "  var win = null;" +
                "  for (var i = 0; i < wins.length; i++) {" +
                "    if (wins[i].isVisible() && wins[i].title && wins[i].title.indexOf('FIFO') >= 0)" +
                "      { win = wins[i]; break; }" +
                "  }" +
                "  if (!win) return 'ERR:no_window';" +
                "  var grids = win.query('gridpanel');" +
                "  if (!grids || !grids.length) return 'ERR:no_grid';" +
                "  var grid = grids[0];" +
                "  var plugin = grid.editingPlugin || grid.findPlugin('cellediting');" +
                "  if (!plugin) return 'ERR:no_plugin';" +
                "  var store = grid.getStore();" +
                "  if (store.getCount() === 0) { store.add({}); }" +
                "  var rec = store.getAt(0);" +
                "  if (!rec) return 'ERR:no_rec';" +
                "  var cols = grid.getColumnManager().getColumns();" +
                "  if (!cols || !cols.length) return 'ERR:no_cols';" +
                // Pass record object (not integer 0) to avoid the ExtJS falsy-zero bug in startEdit
                "  plugin.startEdit(rec, cols[0]);" +
                "  var combo = plugin.activeEditor && plugin.activeEditor.field;" +
                "  if (!combo) return 'ERR:no_combo';" +
                "  return combo.inputEl && combo.inputEl.dom ? combo.inputEl.dom.id : 'ERR:no_input_id';" +
                "} catch(e) { return 'ERR:' + e.message; }";
        Object result = ((JavascriptExecutor) driver).executeScript(script);
        return result != null ? result.toString() : "ERR:null";
    }

}

