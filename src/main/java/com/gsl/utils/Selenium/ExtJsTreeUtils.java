package com.gsl.utils.Selenium;

import com.gsl.driver.DriverManager;
import org.openqa.selenium.JavascriptExecutor;
import java.util.List;

public final class ExtJsTreeUtils {

    private ExtJsTreeUtils() {}

    /**
     * Logs the structure of all ExtJS treepanels in the page.
     * Each node is printed with its tree index, depth, and text.
     *
     * @return List of node descriptions like "Tree 0: Depth 2 → Item"
     */
    @SuppressWarnings("unchecked")
    public static List<String> logAllTreeNodes() {
        String script =
                "var result = [];" +
                        "Ext.ComponentQuery.query('treepanel').forEach(function(tree, treeIndex) {" +
                        "  var root = tree.getStore().getRootNode();" +
                        "  root.cascadeBy(function(node) {" +
                        "    result.push('Tree ' + treeIndex + ': Depth ' + node.getDepth() + ' → ' + node.get('text'));" +
                        "  });" +
                        "});" +
                        "return result;";

        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        List<String> treeData = (List<String>) js.executeScript(script);

        // Print in console
        treeData.forEach(System.out::println);

        return treeData;
    }

    /**
     * Gets the count of child nodes under a given parent text.
     *
     * @param parentText Node text (e.g. "SEMI FINISH", "New Section")
     * @return number of children under that parent
     */
    public static Long getChildNodeCount(String parentText) {
        String script =
                "var count = 0;" +
                        "Ext.ComponentQuery.query('treepanel').forEach(function(tree) {" +
                        "  var root = tree.getStore().getRootNode();" +
                        "  var target = root.findChild('text', arguments[0], true);" +
                        "  if(target) { count = target.childNodes.length; }" +
                        "});" +
                        "return count;";
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        return (Long) js.executeScript(script, parentText);
    }
}
