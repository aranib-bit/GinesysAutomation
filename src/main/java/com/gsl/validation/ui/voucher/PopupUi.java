package com.gsl.validation.ui.voucher;

import com.gsl.driver.DriverManager;
import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiStyleBaseline;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic mechanics for validating a floating ExtJS popup window (Adjustment Entry, Payment Information, ...):
 * every locator is scoped to the window found by its title, so the form behind the popup is never matched.
 * Holds no business rule — what to check, and its expectations, come from the caller and the Excel baseline.
 */
public final class PopupUi {

    private final String title;
    private final String scope;

    public PopupUi(String windowTitle) {
        this.title = windowTitle;
        this.scope = "(//div[contains(@class,'winTitle') and normalize-space()='" + windowTitle
                + "']/ancestor::div[contains(@class,'x-window')][1])";
    }

    public String scope() { return scope; }

    // ── Chrome ──────────────────────────────────────────────────────────────────────────────

    /** Window title text. */
    public List<UiValidationFailure> windowTitle() {
        return UiComponentValidator.validateValue(By.xpath(scope + "//div[contains(@class,'winTitle')]"),
                "POPUP_WINDOW_TITLE", title);
    }

    /** A section header inside the popup: text + font/colour rows. Absent -> SKIP. */
    public List<UiValidationFailure> sectionHeader(String component, String text, UiValidationLevel level) {
        By header = By.xpath(scope + "//div[normalize-space()='" + text + "' and contains(@class,'gsl_TitlePnlType1')]");
        if (!UiComponentValidator.quickPresent(header)) {
            header = By.xpath(scope + "//div[normalize-space()='" + text + "']");
        }
        if (!UiComponentValidator.quickPresent(header)) {
            return UiComponentValidator.skip(component, "element presence", "validated if shown", "not displayed");
        }
        return UiComponentValidator.validateSectionHeader(header, component, text, level);
    }

    /** A label + input field with the full property set, expectations from the baseline. */
    public List<UiValidationFailure> field(String component, String label, String stateKey, FieldStyleSpec spec,
            UiValidationLevel level) {
        By lab = By.xpath(scope + "//span[normalize-space()='" + label + "']");
        By marker = By.xpath(scope + "//span[normalize-space()='" + label + "']/following-sibling::span[normalize-space()='*']");
        By input = inputOf(label);
        return UiComponentValidator.validateFieldBaselined(component, lab, label, marker, input, spec, stateKey, level);
    }

    public By inputOf(String label) {
        return By.xpath(scope + "//span[normalize-space()='" + label
                + "']/ancestor::div[contains(@class,'x-form-item')][1]//*[self::input or self::textarea or (self::div and @role='textbox')][1]");
    }

    /** A button by its visible text: presence, element type, enabled state, style. Absent -> SKIP. */
    public List<UiValidationFailure> button(String component, String text, UiValidationLevel level) {
        By btn = By.xpath(scope + "//span[normalize-space()='" + text + "']/ancestor::a[contains(@class,'x-btn')][1]");
        return UiComponentValidator.validateButtonIfPresent(btn, component, text, FieldStyleSpec.BUTTON, level);
    }

    /** A grid column header inside the popup: text, style, mandatory marker. Absent -> SKIP. */
    public List<UiValidationFailure> gridHeader(String component, String text, UiValidationLevel level) {
        By h = By.xpath(scope + "//span[normalize-space()='" + text + "']");
        if (!UiComponentValidator.quickPresent(h)) {
            return UiComponentValidator.skip(component, "column presence", "validated if shown", "not displayed");
        }
        List<UiValidationFailure> f = new ArrayList<>(UiComponentValidator.validateGridHeader(h, component, text, level));
        f.addAll(UiComponentValidator.validateGridColumnMandatoryBaselined(h, component));
        return f;
    }

    // ── Grid rows ───────────────────────────────────────────────────────────────────────────

    /** Number of data rows in the popup's grid. */
    public int rowCount() {
        try {
            return (int) DriverManager.getDriver().findElements(By.xpath(scope + "//table[@data-recordindex]")).stream()
                    .filter(WebElement::isDisplayed).count();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** The cell of {@code header} in popup grid row {@code rowIndex} (0-based). */
    public By cell(int rowIndex, String header) {
        // By column id, never by counting headers (hidden columns have a header but no cell).
        return com.gsl.utils.Selenium.GridCellLocator.cell(scope, rowIndex, header, null);
    }

    public String cellText(int rowIndex, String header) {
        return UiComponentValidator.readValue(cell(rowIndex, header));
    }

    // ── Footer / labelled values ────────────────────────────────────────────────────────────

    /** Grid-footer fields (label + bold value, e.g. "Record Count:"). Missing -> "". */
    public Map<String, String> footerValues(String... labels) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : labels) {
            m.put(l, UiComponentValidator.readValue(By.xpath(scope + "//span[normalize-space()='" + l
                    + "']/ancestor::div[contains(@class,'x-form-item')][1]//div[@role='textbox']//b")));
        }
        return m;
    }

    /** Read-only amount inputs (e.g. "Transaction Amount:"). Missing -> "". */
    public Map<String, String> amountValues(String... labels) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : labels) {
            m.put(l, UiComponentValidator.readValue(By.xpath(scope + "//span[normalize-space()='" + l
                    + "']/ancestor::div[contains(@class,'x-form-item')][1]//input[@role='textbox']")));
        }
        return m;
    }

    /** One checkpoint of the footer values: each recorded as its own row. */
    public List<UiValidationFailure> recordValues(String prefix, String checkpoint, Map<String, String> values) {
        List<UiValidationFailure> f = new ArrayList<>();
        values.forEach((label, v) -> f.add(new UiValidationFailure(prefix + key(label), "value " + checkpoint, "(recorded)",
                v.isEmpty() ? "(empty)" : v, UiValidationFailure.Status.PASS)));
        return f;
    }

    /** Before -> after of each label, plus an arithmetic/expected check when {@code expectedAfter} has the label. */
    public List<UiValidationFailure> changes(String prefix, String operation, Map<String, String> before,
            Map<String, String> after, Map<String, String> expectedAfter) {
        List<UiValidationFailure> f = new ArrayList<>();
        after.forEach((label, v) -> {
            String comp = prefix + key(label);
            String exp = expectedAfter == null ? null : expectedAfter.get(label);
            String b = before.getOrDefault(label, "");
            if (exp != null) {
                f.add(new UiValidationFailure(comp, "value after " + operation, b + " → " + exp, b + " → " + v,
                        UiComponentValidator.numericEqualsPublic(exp, v)
                                ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            } else {
                f.add(new UiValidationFailure(comp, "value on " + operation, "(recorded)", b + " → " + v,
                        UiValidationFailure.Status.PASS));
            }
        });
        return f;
    }

    public static String key(String label) {
        return label.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Expected/enabled-state keys re-exported so callers need only this class. */
    public static final String BEFORE = UiStyleBaseline.ENABLED_BEFORE_FILL;
    public static final String AFTER = UiStyleBaseline.ENABLED_AFTER_FILL;
}
