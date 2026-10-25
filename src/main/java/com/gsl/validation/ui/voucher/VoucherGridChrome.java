package com.gsl.validation.ui.voucher;

import com.gsl.driver.DriverManager;
import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.pages.enums.Finance.Operations.Common.PaymentPanelField;
import com.gsl.pages.enums.Finance.Operations.Common.SubLedgerGridButtonLabel;
import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiComponentValidator;
import com.gsl.validation.ui.UiComponentValidator.FieldSnapshot;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic mechanics for the parts of a voucher form that look the same on every voucher — the grid
 * toolbar, the grid footer (Record Count / totals), per-row buttons, label-driven read-only fields
 * (Narration, Sub Ledger Balance), the Payment Information panel and collapsible blocks (Adjustment /
 * Payment Information). Scoped to the voucher's own Add/Edit window; knows nothing about any one
 * voucher's business rules (see docs/conventions/utility-layering.md). What is validated, and in
 * which order, is decided by {@link VoucherUiValidationStage}.
 */
public final class VoucherGridChrome {

    private final String scope;

    public VoucherGridChrome(VoucherDomain domain) {
        this.scope = FormSection.voucherWindowScope(domain);
    }

    // ── Grid toolbar ────────────────────────────────────────────────────────────────────────

    private By toolbarIcon(String mdiIconClass) {
        return By.xpath(scope + "//i[contains(@class,'" + mdiIconClass + "')]/ancestor::a[contains(@class,'x-btn')]");
    }

    /** Add Row / Select / Delete Row / Filter icons and the Search icon: presence, element type, state.
     *  @param deleteRowExpectedEnabled {@code false} on a blank grid, {@code null} to skip the state check */
    public List<UiValidationFailure> validateToolbar(Boolean deleteRowExpectedEnabled) {
        List<UiValidationFailure> f = new ArrayList<>();
        By add = toolbarIcon("mdi-playlist-plus");
        By select = toolbarIcon("mdi-playlist-check");
        By delete = toolbarIcon("mdi-playlist-remove");
        By filter = toolbarIcon("mdi-filter-variant");
        By search = By.xpath(scope + "//a[contains(@class,'panelheaderarrow')][.//i[contains(@class,'mdi-magnify')]]");
        f.addAll(UiComponentValidator.validateIconButton(add, "GRID_TOOLBAR_ADD_ROW", true));
        f.addAll(UiComponentValidator.validateIconButton(select, "GRID_TOOLBAR_SELECT", true));
        f.addAll(UiComponentValidator.validateIconButton(delete, "GRID_TOOLBAR_DELETE_ROW", deleteRowExpectedEnabled));
        f.addAll(UiComponentValidator.validateIconButton(filter, "GRID_TOOLBAR_FILTER", true));
        f.addAll(UiComponentValidator.validateIconButton(search, "GRID_TOOLBAR_SEARCH", true));
        return f;
    }

    // ── Grid footer: every label/value the footer shows (Record Count, totals ...) ─────────────

    private By footerFields() {
        // Voucher-neutral: the toolbar that carries the "Record Count" label (GV: gridItems-toolbar; AP/AR: a plain
        // bottom-docked x-toolbar), then every gsl_FieldLabel field in it.
        return By.xpath(scope + "//div[contains(@class,'x-toolbar')][.//span[contains(@class,'x-form-item-label-text')]"
                + "[starts-with(normalize-space(),'Record Count')]]//div[contains(@class,'gsl_FieldLabel')]");
    }

    /** Label → value of every field in the grid footer, in on-screen order. Discovered from the DOM, so
     *  the same code serves GV (Record Count / Total Amount (Dr) / (Cr)) and AP/AR (Record Count / Total). */
    public Map<String, String> readFooter() {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            for (WebElement field : DriverManager.getDriver().findElements(footerFields())) {
                if (!field.isDisplayed()) continue;
                List<WebElement> labels = field.findElements(By.cssSelector(".x-form-item-label-text"));
                if (labels.isEmpty()) continue;
                String label = labels.get(0).getAttribute("textContent").trim();
                List<WebElement> values = field.findElements(By.xpath(".//div[@role='textbox']"));
                String value = values.isEmpty() ? "" : values.get(0).getAttribute("textContent").trim();
                out.put(label, value);
            }
        } catch (RuntimeException ignored) {
            // footer not rendered yet — an empty map is reported as "footer not displayed"
        }
        return out;
    }

    /** One checkpoint of the footer: each label is present and its value is recorded. */
    public List<UiValidationFailure> validateFooter(String checkpoint, Map<String, String> footer) {
        String tag = checkpoint == null ? "" : " (" + checkpoint + ")";
        if (footer.isEmpty()) {
            return List.of(new UiValidationFailure("GRID_FOOTER", "footer presence" + tag, "Record Count / total fields shown",
                    "footer not displayed"));
        }
        List<UiValidationFailure> f = new ArrayList<>();
        for (Map.Entry<String, String> e : footer.entrySet()) {
            String comp = "GRID_FOOTER_" + key(e.getKey());
            f.add(new UiValidationFailure(comp, "label" + tag, e.getKey(), e.getKey(), UiValidationFailure.Status.PASS));
            f.add(new UiValidationFailure(comp, "value" + tag, "(recorded)", e.getValue().isEmpty() ? "(empty)" : e.getValue(),
                    UiValidationFailure.Status.PASS));
        }
        return f;
    }

    /**
     * Footer before vs after a row operation. The Record Count field must change by
     * {@code expectedRecordDelta}; every other field (totals) is reported before → after, and compared
     * with {@code expectedAfter} when the caller knows the figure (label → expected value).
     */
    public List<UiValidationFailure> validateFooterChange(String operation, Map<String, String> before,
            Map<String, String> after, int expectedRecordDelta, Map<String, String> expectedAfter) {
        List<UiValidationFailure> f = new ArrayList<>();
        for (Map.Entry<String, String> e : after.entrySet()) {
            String label = e.getKey();
            String comp = "GRID_FOOTER_" + key(label);
            String b = before.get(label);
            if (label.toLowerCase().contains("record count")) {
                f.addAll(UiComponentValidator.validateCountPair(comp, "value on " + operation,
                        parseCount(b), parseCount(e.getValue()), expectedRecordDelta));
                continue;
            }
            String expected = expectedAfter == null ? null : expectedAfter.get(label);
            if (expected != null) {
                f.add(new UiValidationFailure(comp, "value after " + operation, expected, e.getValue(),
                        UiComponentValidator.numericEqualsPublic(expected, e.getValue())
                                ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            } else {
                f.add(new UiValidationFailure(comp, "value on " + operation, "(recorded)",
                        (b == null ? "(none)" : b) + " → " + e.getValue(), UiValidationFailure.Status.PASS));
            }
        }
        if (after.isEmpty()) {
            f.add(new UiValidationFailure("GRID_FOOTER", "footer presence after " + operation,
                    "Record Count / total fields shown", "footer not displayed"));
        }
        return f;
    }

    private static Integer parseCount(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String key(String label) {
        return label.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    // ── Per-row buttons (Adjust, Cheque/RTGS, Site) ─────────────────────────────────────────────

    /** Every row button that exists on row {@code rowIndex} (0-based {@code data-recordindex}) is validated;
     *  one that does not exist for this voucher is a SKIP ("not present") — never a failure. */
    public List<UiValidationFailure> validateRowButtons(int rowIndex, UiValidationLevel level) {
        List<UiValidationFailure> f = new ArrayList<>();
        for (SubLedgerGridButtonLabel b : new SubLedgerGridButtonLabel[]{
                SubLedgerGridButtonLabel.ADJUST, SubLedgerGridButtonLabel.CHEQUE_RTGS, SubLedgerGridButtonLabel.SITE}) {
            By locator = By.xpath("//table[@data-recordindex='" + rowIndex + "']//button[normalize-space()='" + b.label() + "']");
            f.addAll(UiComponentValidator.validateButtonIfPresent(locator,
                    "GRID_ROW_BUTTON_" + b.name() + "_ROW_" + (rowIndex + 1), b.label(), FieldStyleSpec.GRID_ROW_BUTTON, level));
        }
        return f;
    }

    // ── Label-driven read-only fields (Narration, Sub Ledger Balance) ────────────────────────────

    /** Input of the form item whose label starts with {@code label} (colon ignored), inside the voucher window. */
    public By labelledField(String label) {
        return By.xpath(scope + "//span[contains(@class,'x-form-item-label-text')][starts-with(normalize-space(),'" + label
                + "')]/ancestor::*[contains(@class,'x-form-item')][1]"
                + "//*[self::input or self::textarea or (self::div and @role='textbox')]");
    }

    /** {@link #labelledField} with the full baselined property set (element type, mandatory, state, value, style). */
    public List<UiValidationFailure> validateLabelledFieldBaselined(String component, String label, String checkpoint) {
        By lab = By.xpath(scope + "//span[contains(@class,'x-form-item-label-text')][starts-with(normalize-space(),'" + label + "')]");
        By marker = By.xpath(scope + "//span[contains(@class,'x-form-item-label-text')][starts-with(normalize-space(),'" + label
                + "')]/following-sibling::span[normalize-space()='*']");
        List<UiValidationFailure> f = new ArrayList<>(UiComponentValidator.validateFieldBaselined(component, lab, label + ":",
                marker, labelledField(label), FieldStyleSpec.READONLY_AMOUNT, com.gsl.validation.ui.UiStyleBaseline.ENABLED_AFTER_FILL, UiValidationLevel.CONTRACT));
        return f;
    }

    public FieldSnapshot capture(String label) {
        return UiComponentValidator.captureField(labelledField(label));
    }

    /** Presence + element type + state/value of a label-driven field at ONE checkpoint. State is asserted
     *  only when the Excel baseline defines it ({@code baselineStateKey}); otherwise it is recorded. Absent → SKIP "not displayed". */
    public List<UiValidationFailure> validateLabelledField(String component, String label, String checkpoint,
            String baselineStateKey) {
        UiFieldState expectedState = baselineStateKey == null ? null
                : UiComponentValidator.baselineStateOf(component, baselineStateKey);
        By locator = labelledField(label);
        if (!UiComponentValidator.quickPresent(locator)) {
            return UiComponentValidator.skip(component, "element presence", "validated if shown", "not displayed");
        }
        List<UiValidationFailure> f = new ArrayList<>(UiComponentValidator.validateElementTypeBaselined(locator, component));
        FieldSnapshot s = UiComponentValidator.captureField(locator);
        if (expectedState != null) {
            f.add(new UiValidationFailure(component, "state " + checkpoint, expectedState.name(), s.stateText(),
                    s.state() == expectedState ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        } else {
            f.add(new UiValidationFailure(component, "state " + checkpoint, "(recorded)", s.stateText(),
                    UiValidationFailure.Status.PASS));
        }
        f.add(new UiValidationFailure(component, "value " + checkpoint, "(recorded)", s.valueText(),
                UiValidationFailure.Status.PASS));
        return f;
    }

    // ── Payment Information panel (read-only mirror of the selected row's payment) ───────────────

    /** Snapshot of every Payment Information panel field the voucher shows (absent captions omitted). */
    public Map<String, FieldSnapshot> capturePaymentPanel() {
        Map<String, FieldSnapshot> m = new LinkedHashMap<>();
        for (PaymentPanelField field : PaymentPanelField.values()) {
            if (UiComponentValidator.quickPresent(field.labelLocator(scope))) {
                m.put(field.key(), UiComponentValidator.captureField(field.valueLocator(scope)));
            }
        }
        return m;
    }

    /** Payment panel before vs after a grid line is selected (state + value of each shown field). */
    public List<UiValidationFailure> validatePaymentPanelAfterSelection(Map<String, FieldSnapshot> before,
            Map<String, FieldSnapshot> after) {
        if (before.isEmpty() && after.isEmpty()) {
            return UiComponentValidator.skip("PAYMENT_PANEL", "element presence", "validated if shown", "panel fields not displayed");
        }
        List<UiValidationFailure> f = new ArrayList<>();
        for (Map.Entry<String, FieldSnapshot> e : after.entrySet()) {
            FieldSnapshot b = before.get(e.getKey());
            if (b == null) {
                f.addAll(UiComponentValidator.skip(e.getKey(), "state before line selection", "shown", "not displayed before selection"));
                b = e.getValue();
            }
            f.addAll(UiComponentValidator.validateFieldBeforeAfterBaselined(e.getKey(), "line selection", b, e.getValue(), null, null));
        }
        return f;
    }

    // ── Collapsible block (Adjustment Information / Payment Information) ─────────────────────────

    /** Block presence and OPEN/CLOSED state. Not on the form → one SKIP "not present" (never a FAIL). */
    public List<UiValidationFailure> validateBlockPresentAndState(FormSection section, Boolean expectedOpen,
            UiValidationLevel level) {
        if (!UiComponentValidator.quickPresent(section.titleLocator(scope))) {
            return UiComponentValidator.skip("SECTION_" + section.key(), "section presence",
                    "validated if the form shows it", "not present");
        }
        List<UiValidationFailure> f = new ArrayList<>();
        f.add(new UiValidationFailure("SECTION_" + section.key(), "section presence", "present", "present",
                UiValidationFailure.Status.PASS));
        f.addAll(UiComponentValidator.validateSectionHeader(section.titleLocator(scope),
                "SECTION_HEADER_" + section.key(), section.title(), level));
        if (expectedOpen != null) {
            f.addAll(UiComponentValidator.validateSectionState(section, scope, expectedOpen));
        } else {
            f.addAll(UiComponentValidator.recordSectionState(section, scope));
        }
        return f;
    }

    /** Current OPEN/CLOSED text of a section, or {@code "NOT PRESENT"}. */
    public String sectionStateText(FormSection section) {
        return UiComponentValidator.readSectionState(section, scope);
    }

    public String scope() { return scope; }

    // ── Main grid rows ──────────────────────────────────────────────────────────────────────

    /** Cell of the voucher's own grid: row {@code rowIndex} (0-based), column {@code header} (+ its itemId, if any).
     *  Located by column id, never by counting columns - see {@link com.gsl.utils.Selenium.GridCellLocator}. */
    public By gridCell(int rowIndex, String header, String itemId) {
        return com.gsl.utils.Selenium.GridCellLocator.cell(scope, rowIndex, header, itemId);
    }

    public By gridCell(int rowIndex, String header) {
        return gridCell(rowIndex, header, null);
    }

    public String gridCellText(int rowIndex, String header, String itemId) {
        return UiComponentValidator.readValue(gridCell(rowIndex, header, itemId));
    }

    public String gridCellText(int rowIndex, String header) {
        return UiComponentValidator.readValue(gridCell(rowIndex, header));
    }

    /** Selects the grid row by clicking its leading (row-actions) cell; never throws - returns false when it could not. */
    public boolean selectRow(int rowIndex) {
        try {
            By first = By.xpath("(" + scope + "//table[@data-recordindex='" + rowIndex + "']//td)[1]");
            WebElement el = DriverManager.getDriver().findElement(first);
            el.click();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
