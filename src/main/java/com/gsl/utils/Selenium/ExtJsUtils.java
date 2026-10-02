package com.gsl.utils.Selenium;

import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * JavaScript-executor helpers for ExtJS form controls that don't expose reliable DOM-level
 * signals to plain Selenium locators/waits: radio-group selection and readback (see the note
 * above {@link #selectExtJsRadio(String)} on why ExtJS radios need this), setting a combobox's
 * value directly via its {@code Ext.ComponentQuery} component (bypassing the picker UI),
 * writing a grid cell's underlying store record, and appending a row to the Price Information
 * grid.
 *
 * <p>Prefer the {@link RadioFieldLabel}-typed overloads of {@link #selectExtJsRadio} /
 * {@link #getSelectedExtJsRadioLabel} from page-object code — they keep field-heading strings
 * out of page objects and in one typed enum location.
 */
public class ExtJsUtils {

    private ExtJsUtils() {}

    // ── ExtJS Radio Button Utilities ─────────────────────────────────────────
    //
    // ExtJS radio buttons do NOT update the DOM `checked` attribute when selected.
    // State is tracked internally; the only reliable DOM signal is the CSS class
    // `x-form-cb-checked` is added to the outer .x-form-item div, not the <input>.
    // Use descendant selector `.x-form-cb-checked .x-form-cb-label` to read the label.
    // XPath attribute checks on `checked` will always fail — use JS for detection.

    /**
     * Implement this interface on enums that represent ExtJS radio-group field labels.
     * Keeps field heading strings out of page-object code and in one typed location.
     *
     * <pre>{@code
     * public enum VoucherDocumentFormLabel implements ExtJsUtils.RadioFieldLabel {
     *     ON_ACCOUNT("On Account:");
     *     ...
     * }
     * }</pre>
     */
    public interface RadioFieldLabel {
        String getFieldLabel();
    }

    // ── Enum-typed overloads (preferred — eliminates magic strings) ───────────

    /** {@link RadioFieldLabel}-typed overload of {@link #selectExtJsRadio(String, String)}. */
    public static void selectExtJsRadio(RadioFieldLabel field, String optionLabel) {
        selectExtJsRadio(field.getFieldLabel(), optionLabel);
    }

    /** {@link RadioFieldLabel}-typed overload of {@link #getSelectedExtJsRadioLabel(String)}. */
    public static String getSelectedExtJsRadioLabel(RadioFieldLabel field) {
        return getSelectedExtJsRadioLabel(field.getFieldLabel());
    }

    // ── String overloads (fallback / one-off usage) ───────────────────────────

    /**
     * Clicks a radio option on the page by its visible label text.
     * Use when only one radio group is present on the page.
     */
    public static void selectExtJsRadio(String optionLabel) {
        SeleniumUtils.click(
                By.xpath("//label[contains(@class,'x-form-cb-label') and normalize-space()='" + optionLabel + "']"),
                WaitType.CLICKABLE,
                "RADIO_" + optionLabel
        );
        JavascriptExecutorUtils.waitForExtJS();
    }

    /**
     * Clicks a radio option scoped to a named field label.
     * Use when multiple radio groups share the same option labels (e.g. two "Yes/No" groups).
     *
     * @param fieldLabel  the field heading text, e.g. {@code "On Account:"}
     * @param optionLabel the option to select, e.g. {@code "Yes"} or {@code "No"}
     */
    public static void selectExtJsRadio(String fieldLabel, String optionLabel) {
        SeleniumUtils.click(
                By.xpath("//span[contains(@class,'x-form-item-label-text') and normalize-space()='" + fieldLabel + "']" +
                         "/ancestor::div[contains(@class,'x-form-item')]" +
                         "//label[contains(@class,'x-form-cb-label') and normalize-space()='" + optionLabel + "']"),
                WaitType.CLICKABLE,
                "RADIO_" + fieldLabel + "_" + optionLabel
        );
        JavascriptExecutorUtils.waitForExtJS();
    }

    /**
     * Returns the label text of the currently selected radio option on the page.
     * Use when only one radio group is present on the page.
     *
     * @return option label text (e.g. {@code "Yes"}), or {@code null} if nothing is selected
     */
    public static String getSelectedExtJsRadioLabel() {
        return (String) ((JavascriptExecutor) DriverManager.getDriver()).executeScript(
                "var label = document.querySelector('.x-form-cb-checked .x-form-cb-label');" +
                "return label ? label.innerText.trim() : null;"
        );
    }

    /**
     * Returns the selected radio option label for a specific named field.
     * Use when multiple radio groups are present on the same page.
     *
     * @param fieldLabel the field heading text, e.g. {@code "On Account:"}
     * @return option label text (e.g. {@code "Yes"}), or {@code null} if nothing is selected
     */
    public static String getSelectedExtJsRadioLabel(String fieldLabel) {
        return (String) ((JavascriptExecutor) DriverManager.getDriver()).executeScript(
                "var target = arguments[0];" +
                "var fieldSpan = [...document.querySelectorAll('.x-form-item-label-text')]" +
                "  .find(function(el){ return el.innerText.trim() === target; });" +
                "if (!fieldSpan) return null;" +
                "var formItem = fieldSpan.closest('.x-form-item');" +
                "if (!formItem) return null;" +
                "var label = formItem.querySelector('.x-form-cb-checked .x-form-cb-label');" +
                "return label ? label.innerText.trim() : null;",
                fieldLabel
        );
    }

    // ────────────────────────────────────────────────────────────────────────

    /**
     * Sets an ExtJS combobox's value directly via {@code Ext.ComponentQuery}, looked up by its
     * {@code fieldLabel} config — expands the combo, loads its store, finds the record whose
     * display field matches {@code displayValue}, and selects it, bypassing the picker UI/typing.
     *
     * @throws org.openqa.selenium.JavascriptException if no combo has the given field label, or
     *         {@code displayValue} isn't present in the store
     */
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

    /**
     * Writes {@code value} directly into a grid's underlying store record (selected by
     * {@code gridIndex}/{@code rowIndex} and written to {@code dataIndex}) and refreshes the
     * view — bypasses cell-editing UI entirely.
     */
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

    /**
     * Same as {@link #setExtJsComboByLabel(String, String)}, but looks the combo up by its
     * {@code name} config instead of {@code fieldLabel}.
     */
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


    /**
     * Adds a row to the Price Information grid ({@code itemId=grd_PriceInfo}) via its underlying
     * store — converts {@code effectiveDate} to a JS {@code Date} on a DD/MM/YYYY-safe path —
     * and, best-effort, opens the cell editor on the new row's RSP column to trigger a UI refresh.
     */
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
