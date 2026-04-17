package com.gsl.utils.Selenium;

import com.gsl.driver.DriverManager;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class ExtJsUtils {

    private ExtJsUtils() {}

    public static void setExtJsComboByLabel(String fieldLabel, String displayValue) {

        String script =
                "var c = Ext.ComponentQuery.query('combobox[fieldLabel=\"" + fieldLabel + "\"]')[0];" +
                        "if (!c) { throw 'Combo not found: " + fieldLabel + "'; }" +

                        // ensure store loaded
                        "c.expand();" +
                        "var store = c.getStore();" +

                        "store.load({" +
                        "  callback: function() {" +
                        "    var r = null;" +
                        "    var targetVal = '" + displayValue + "';" +
                        "    store.each(function(record) {" +
                        "      if (record.get(c.displayField) === targetVal) { r = record; return false; }" +
                        "    });" +
                        "    if (!r) { throw 'Value not in store: ' + targetVal; }" +

                        // select properly
                        "    c.select(r);" +
                        "    c.fireEvent('change', c, r.get(c.valueField));" +
                        "    c.fireEvent('blur', c);" +
                        "    c.collapse();" +
                        "    c.clearInvalid();" +
                        "  }" +
                        "});";

        ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script);
    }

    public static void setGridCellValue(
            WebDriver driver,
            int gridIndex,
            int rowIndex,
            String dataIndex,
            String value) {

        String script =
                "var grid = Ext.ComponentQuery.query('grid')[" + gridIndex + "];" +
                        "if (!grid) return 'GRID_NOT_FOUND';" +

                        "var store = grid.getStore();" +
                        "var rec = store.getAt(" + rowIndex + ");" +
                        "if (!rec) return 'ROW_NOT_FOUND';" +

                        "rec.set('" + dataIndex + "', '" + value + "');" +
                        "rec.commit();" +
                        "grid.getView().refresh();" +

                        "return 'OK';";

        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        Object result = js.executeScript(script);
        System.out.println("JS Result: " + result);
    }

    public static void setExtJsComboByName(String name, String displayValue) {

        String script =
                "var c = Ext.ComponentQuery.query('combobox[name=\"" + name + "\"]')[0];" +
                        "if (!c) throw 'Combo not found: " + name + "';" +

                        "c.expand();" +
                        "var store = c.getStore();" +

                        "store.load({" +
                        "  callback: function() {" +
                        "    var r = null;" +
                        "    var targetVal = '" + displayValue + "';" +
                        "    store.each(function(record) {" +
                        "      if (record.get(c.displayField) === targetVal) { r = record; return false; }" +
                        "    });" +

                        "    if (!r) throw 'Value not found: ' + targetVal;" +

                        "    c.select(r);" +
                        "    c.fireEvent('select', c, r);" +
                        "    c.fireEvent('change', c, r.get(c.valueField));" +
                        "    c.blur();" +
                        "    c.collapse();" +
                        "  }" +
                        "});";

        ((JavascriptExecutor) DriverManager.getDriver()).executeScript(script);
    }


    public static void addPriceInfo(LocalDate effectiveDate, double mrp, double rsp) {

        // Format date as DD/MM/YYYY
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String effDateStr = effectiveDate.format(formatter);

        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();

        js.executeScript(
                "var priceGrid = Ext.ComponentQuery.query('grid[itemId=grd_PriceInfo]')[0];" +
                        "if (!priceGrid) throw 'Price grid not found';" +

                        "var priceStore = priceGrid.getStore();" +

                        // ✅ Convert string → JS Date (DD/MM/YYYY safe parsing)
                        "var parts = arguments[0].split('/');" +
                        "var effDate = new Date(parts[2], parts[1] - 1, parts[0]);" +

                        "var priceRec = priceStore.add({" +
                        "   effectiveDate: effDate," +  // <-- MATCH grid dataIndex
                        "   rsp: arguments[1]," +
                        "   mrp: arguments[2]" +
                        "})[0];" +

                        "priceStore.commitChanges(); " +  // ✅ commit to update UI

                        // Optional: start edit on RSP column to trigger UI refresh
                        "var pricePlugin = priceGrid.findPlugin('cellediting');" +
                        "if (pricePlugin) {" +
                        "   var rspCol = priceGrid.getColumnManager().getColumns()" +
                        "       .find(c => c.dataIndex === 'rsp');" +
                        "   pricePlugin.startEdit(priceRec, rspCol);" +
                        "}",

                effDateStr,   // pass STRING
                rsp,
                mrp
        );
    }







}
