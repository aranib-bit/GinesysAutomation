package com.gsl.utils.Selenium;

import com.gsl.driver.DriverManager;
import com.gsl.pages.enums.Inventory.Product_Definition.GroupHierarchyType;
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
        new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(10)).until(
                driver -> (Boolean) ((JavascriptExecutor) driver).executeScript(
                        "return !!(window.Ext && window.Ext.isReady && " +
                                "!window.Ext.Ajax.isLoading() && " +
                                "window.Ext.ComponentQuery.query('[isMasked=true]').length === 0);"
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

    public static void setSubLedger(WebDriver driver, String slName) {
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





}

