package com.gsl.validation.ui;

import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.utils.ui.FieldStyleSpec;
import com.gsl.utils.ui.UiFieldState;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reusable, component-level UI-contract validators — one method per component TYPE (label,
 * required marker, input, button, section header, status indicator, grid header/cell), each
 * usable by any field/page that supplies its own locators. This is the single implementation the
 * doc's proposed {@code FontValidator}/{@code ColorValidator}/{@code DimensionValidator}/etc.
 * hierarchy collapses into — mechanically, every one of those is "read N computed-style properties
 * off an element and compare to a {@link FieldStyleSpec} entry," so one class with one method per
 * component TYPE covers it without a 10-class hierarchy.
 *
 * <p>Every method returns a {@code List<UiValidationFailure>} rather than asserting directly —
 * callers (page-level orchestrators such as {@code GeneralVoucherAdd#validateUiContract}) collect
 * every returned list and soft-assert them all at once, the same collect-all-then-assert convention
 * already used by this codebase's DB/snapshot validators.
 */
public final class UiComponentValidator {

    private UiComponentValidator() {}

    private static final String[] STYLE_PROPERTIES =
            {"fontSize", "fontFamily", "fontWeight", "color", "backgroundColor", "borderColor"};

    // ── Style comparison plumbing ──────────────────────────────────────────

    private static List<UiValidationFailure> checkStyle(WebElement element, String componentName, FieldStyleSpec spec) {
        return checkStyle(element, componentName, spec, null);
    }

    /** {@code state}-aware overload: when {@code state == DISABLED}, the expected background color
     *  comes from the baseline's {@link FieldStyleSpec#DISABLED_STATE} row regardless of {@code spec}'s own
     *  (EDITABLE-state) background — e.g. Remarks-on-blank-Add-form/Owner-Site-on-Edit are
     *  correctly {@code DISABLED} but would otherwise be checked against {@code TEXT_INPUT}'s
     *  editable-state white background and false-fail. {@code null} state (or any non-DISABLED
     *  state) falls back to {@code spec}'s own background, unchanged from before. */
    private static List<UiValidationFailure> checkStyle(WebElement element, String componentName,
            FieldStyleSpec spec, UiFieldState state) {
        List<UiValidationFailure> failures = new ArrayList<>();
        Map<String, String> computed = SeleniumUtils.getComputedStyle(element, componentName, STYLE_PROPERTIES);
        FieldStyleSpec backgroundSpec = state == UiFieldState.DISABLED ? FieldStyleSpec.DISABLED_STATE : spec;
        boolean[] missingReported = {false};
        checkOne(failures, componentName, spec, UiStyleBaseline.FONT_SIZE, computed.get("fontSize"), missingReported);
        checkOne(failures, componentName, spec, UiStyleBaseline.FONT_FAMILY, computed.get("fontFamily"), missingReported);
        checkOne(failures, componentName, spec, UiStyleBaseline.FONT_WEIGHT, computed.get("fontWeight"), missingReported);
        checkOne(failures, componentName, spec, UiStyleBaseline.COLOR, computed.get("color"), missingReported);
        checkOne(failures, componentName, backgroundSpec, UiStyleBaseline.BACKGROUND, computed.get("backgroundColor"), missingReported);
        checkOne(failures, componentName, spec, UiStyleBaseline.BORDER, computed.get("borderColor"), missingReported);
        return failures;
    }

    /** The expected value comes from the Excel baseline ({@link UiStyleBaseline#resolve}): "not
     *  checked" (blank/{@code -} cell) records nothing; "no baseline anywhere for this spec" records
     *  ONE visible SKIP per check (never a silent pass). Otherwise ALWAYS records one entry (PASS or
     *  FAIL) per property checked, not just on mismatch — a clean run then proves the checks actually
     *  ran, not just that nothing broke. */
    private static void checkOne(List<UiValidationFailure> results, String component, FieldStyleSpec spec,
                                  String property, String actual, boolean[] missingReported) {
        UiStyleBaseline.Resolved resolved = UiStyleBaseline.resolve(spec, component, property);
        if (resolved.kind() == UiStyleBaseline.Kind.NOT_CHECKED) return;
        if (resolved.kind() == UiStyleBaseline.Kind.MISSING) {
            if (!missingReported[0]) {
                missingReported[0] = true;
                results.add(new UiValidationFailure(component, "style baseline", "defined in Excel (" + spec.name() + ")",
                        "no baseline in Excel", UiValidationFailure.Status.SKIP));
            }
            return;
        }
        String expected = resolved.value();
        String actualTrimmed = actual == null ? "" : actual.trim();
        UiValidationFailure.Status status = expected.equalsIgnoreCase(actualTrimmed)
                ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL;
        results.add(new UiValidationFailure(component, property, expected, actual, status));
    }

    /** Trimmed text with non-breaking / zero-width spaces treated as ordinary whitespace — an empty
     *  ExtJS cell reads as a lone {@code &nbsp;}, which {@link String#trim()} leaves in place. */
    private static String norm(String s) {
        return s == null ? "" : s.replace(' ', ' ').replace("​", "").strip();
    }

    private static void checkText(List<UiValidationFailure> results, String component, String property,
                                   String expected, String actual) {
        if (expected == null) return;
        String actualTrimmed = norm(actual);
        UiValidationFailure.Status status = norm(expected).equalsIgnoreCase(actualTrimmed)
                ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL;
        results.add(new UiValidationFailure(component, property, expected, actual, status));
    }

    // ── Resilience plumbing ───────────────────────────────────────────────
    //
    // Every public method below resolves at least one locator via WaitUtils, which THROWS
    // (TimeoutException/NoSuchElementException) when the element genuinely isn't on the page — a
    // real, expected outcome for a placeholder/not-yet-confirmed locator (see FieldStyleSpec's own
    // "placeholder values" convention for style VALUES; a wrong LOCATOR needs the identical
    // treatment). Left unguarded, that throw aborts the caller's entire validateUiContract() sweep
    // — confirmed live (run 20261001_000650/_003820): one wrong section-header locator killed every
    // other check in the same run before it could even attempt them. `guarded` turns that abort
    // into exactly one UiValidationFailure ("element presence") and lets the rest of the sweep
    // proceed, so a wrong locator is reported the same way a wrong style value already is — visible
    // in the soft-assert output, not a crash.

    private static List<UiValidationFailure> guarded(String componentName,
            java.util.function.Supplier<List<UiValidationFailure>> check) {
        try {
            return check.get();
        } catch (RuntimeException e) {
            return List.of(new UiValidationFailure(componentName, "element presence",
                    "element found", "NOT FOUND — " + e.getClass().getSimpleName()
                    + " (locator likely wrong/unverified — see this component's own locator Javadoc)"));
        }
    }

    // ── Label ────────────────────────────────────────────────────────────

    /** Validates a field label's text (if {@code expectedText} is non-null) and, at
     *  {@link UiValidationLevel#CONTRACT}, its style against {@code spec}. */
    public static List<UiValidationFailure> validateLabel(By labelLocator, String componentName,
            String expectedText, FieldStyleSpec spec, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement label = WaitUtils.untilVisible(labelLocator);
            String actualText = SeleniumUtils.getAttribute(label, "textContent", componentName + "_LABEL");
            checkText(failures, componentName, "label text", expectedText, actualText);
            if (level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(label, componentName + "_LABEL", spec));
            }
            return failures;
        });
    }

    // ── Required marker ─────────────────────────────────────────────────

    /** Validates that the required-field marker (`*`) is present exactly when {@code expectedRequired}
     *  says it should be, and (at CONTRACT level, only when present) its style. */
    public static List<UiValidationFailure> validateRequiredMarker(By markerLocator, String componentName,
            boolean expectedRequired, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            boolean present = SeleniumUtils.isElementPresent(markerLocator, 2);
            UiValidationFailure.Status presenceStatus = present == expectedRequired
                    ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL;
            failures.add(new UiValidationFailure(componentName + "_UI_MANDATORY", "mandatory marker present",
                    String.valueOf(expectedRequired), String.valueOf(present), presenceStatus));
            if (present && level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(WaitUtils.untilVisible(markerLocator),
                        componentName + "_UI_MANDATORY", FieldStyleSpec.REQUIRED_MARKER));
            }
            return failures;
        });
    }

    // ── Section open/closed state ────────────────────────────────────────

    /** Checks a collapsible section's open/closed state — read only, never toggles. State comes from
     *  the section's chevron icon class ({@link FormSection#CHEVRON_OPEN_CLASS} = OPEN,
     *  {@link FormSection#CHEVRON_CLOSED_CLASS} = CLOSED); any other class is reported verbatim as
     *  the actual value and fails. Reported as {@code SECTION_<key>} so the reporter can surface the
     *  state badge in the group header.
     *
     *  @param scopeXpath {@code ""} for the whole page, or a window/panel scope to search under */
    public static List<UiValidationFailure> validateSectionState(FormSection section, String scopeXpath,
            boolean expectedOpen) {
        String component = "SECTION_" + section.key();
        return guarded(component, () -> {
            WebElement icon = WaitUtils.untilVisible(section.chevronLocator(scopeXpath));
            String cls = SeleniumUtils.getAttribute(icon, "class", component + "_CHEVRON");
            String actual = cls != null && cls.contains(FormSection.CHEVRON_OPEN_CLASS) ? "OPEN"
                    : cls != null && cls.contains(FormSection.CHEVRON_CLOSED_CLASS) ? "CLOSED"
                    : "UNKNOWN (" + cls + ")";
            String expected = expectedOpen ? "OPEN" : "CLOSED";
            return List.of(new UiValidationFailure(component, "section state", expected, actual,
                    expected.equals(actual) ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        });
    }

    // ── Skip / value helpers ─────────────────────────────────────────────

    /** One SKIP entry ("not applicable" / "not displayed") — never soft-asserted. */
    public static List<UiValidationFailure> skip(String component, String property, String expected, String actual) {
        return List.of(new UiValidationFailure(component, property, expected, actual, UiValidationFailure.Status.SKIP));
    }

    /** Numeric value check — reads the element's {@code value} attribute (inputs) else its text,
     *  and compares as numbers ("200" equals "200.00"). Style is checked too at CONTRACT level when
     *  {@code spec} is non-null. */
    public static List<UiValidationFailure> validateAmountValue(By locator, String componentName,
            String expectedAmount, FieldStyleSpec spec, UiFieldState state, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement el = WaitUtils.untilVisible(locator);
            String actual = SeleniumUtils.getAttribute(el, "value", componentName + "_VALUE_ATTR");
            if (actual == null || actual.isBlank()) {
                actual = SeleniumUtils.getAttribute(el, "textContent", componentName + "_VALUE");
            }
            failures.add(new UiValidationFailure(componentName, "value", expectedAmount, actual,
                    numericEquals(expectedAmount, actual)
                            ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            if (spec != null && level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(el, componentName, spec, state));
            }
            return failures;
        });
    }

    /** Compares the element's text content to {@code expectedText} (trimmed, case-insensitive). */
    public static List<UiValidationFailure> validateValue(By locator, String componentName, String expectedText) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement el = WaitUtils.untilVisible(locator);
            String actual = SeleniumUtils.getAttribute(el, "textContent", componentName + "_VALUE");
            if (actual == null || actual.isBlank()) {
                actual = SeleniumUtils.getAttribute(el, "value", componentName + "_VALUE_ATTR");
            }
            checkText(failures, componentName, "value", expectedText, actual);
            return failures;
        });
    }

    // ── Field state (editable / readonly / disabled) ───────────────────

    /** Validates a field's interaction state via {@code aria-disabled}/{@code aria-readonly} AND
     *  the native {@code disabled} attribute. ExtJS usually reflects disabled-ness through ARIA
     *  attributes rather than the native attribute (see {@code VoucherFooterBase
     *  #waitUntilSaveEnabled} / {@code GeneralVoucherDocumentInformation#isSavedDocumentNoReadOnly}),
     *  but AP's Remarks combo is a confirmed counter-example (user-supplied live DOM, 2026-10-01):
     *  {@code aria-disabled="false"} while the native {@code disabled=""} attribute IS present —
     *  ExtJS set the real disabling attribute but didn't sync the ARIA mirror for this field. Per
     *  Selenium/W3C semantics, {@code getAttribute("disabled")} returns {@code "true"} when the
     *  attribute exists at all (regardless of its literal value, including {@code disabled=""}) and
     *  {@code null} when absent — so checking for non-null here, OR'd with {@code aria-disabled},
     *  catches both signalling styles without weakening the check for fields that only use ARIA. */
    public static List<UiValidationFailure> validateFieldState(By inputLocator, String componentName,
            UiFieldState expectedState) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement input = WaitUtils.untilVisible(inputLocator);
            String ariaDisabled = SeleniumUtils.getAttribute(input, "aria-disabled", componentName + "_ARIA_DISABLED");
            String ariaReadonly = SeleniumUtils.getAttribute(input, "aria-readonly", componentName + "_ARIA_READONLY");
            String nativeDisabled = SeleniumUtils.getAttribute(input, "disabled", componentName + "_DISABLED");
            boolean disabled = "true".equalsIgnoreCase(ariaDisabled) || nativeDisabled != null;
            boolean readonly = "true".equalsIgnoreCase(ariaReadonly);
            UiFieldState actualState = disabled ? UiFieldState.DISABLED
                    : readonly ? UiFieldState.READONLY
                    : UiFieldState.EDITABLE;
            UiValidationFailure.Status stateStatus = actualState == expectedState
                    ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL;
            failures.add(new UiValidationFailure(componentName, "field state",
                    expectedState.name(), actualState.name(), stateStatus));
            return failures;
        });
    }

    /** The field must not be editable — DISABLED or READONLY both pass; the actual state is shown.
     *  Used where the business rule is "never editable" and the app signals it either way (e.g.
     *  Cash/Bank Balance before and after Cash/Bank selection). */
    public static List<UiValidationFailure> validateNotEditable(By inputLocator, String componentName) {
        return guarded(componentName, () -> {
            WebElement input = WaitUtils.untilVisible(inputLocator);
            String ariaDisabled = SeleniumUtils.getAttribute(input, "aria-disabled", componentName + "_ARIA_DISABLED");
            String ariaReadonly = SeleniumUtils.getAttribute(input, "aria-readonly", componentName + "_ARIA_READONLY");
            String nativeDisabled = SeleniumUtils.getAttribute(input, "disabled", componentName + "_DISABLED");
            String nativeReadonly = SeleniumUtils.getAttribute(input, "readonly", componentName + "_READONLY");
            boolean disabled = "true".equalsIgnoreCase(ariaDisabled) || nativeDisabled != null;
            boolean readonly = "true".equalsIgnoreCase(ariaReadonly) || nativeReadonly != null;
            String actual = disabled ? "DISABLED" : readonly ? "READONLY" : "EDITABLE";
            return List.of(new UiValidationFailure(componentName, "field state", "NOT EDITABLE (disabled/readonly)",
                    actual, "EDITABLE".equals(actual) ? UiValidationFailure.Status.FAIL : UiValidationFailure.Status.PASS));
        });
    }

    // ── Input style ──────────────────────────────────────────────────────

    /** Validates an input's style against {@code spec} — no-op below CONTRACT level.
     *  {@code expectedState} lets the background-color check follow the field's actual expected
     *  state (see the {@code state}-aware {@code checkStyle} overload) instead of always assuming
     *  EDITABLE. */
    public static List<UiValidationFailure> validateInputStyle(By inputLocator, String componentName,
            FieldStyleSpec spec, UiFieldState expectedState, UiValidationLevel level) {
        if (level != UiValidationLevel.CONTRACT) return List.of();
        return guarded(componentName,
                () -> checkStyle(WaitUtils.untilVisible(inputLocator), componentName, spec, expectedState));
    }

    // ── Composite: label + required marker + input style + input state ──

    /** The full standard-field checklist (doc §24) in one call: label text+style, required-marker
     *  presence+style, input style, input state — the shape every {@code validate<Field>Ui} method
     *  on {@code VoucherDocumentInformationBase} delegates to. */
    public static List<UiValidationFailure> validateField(String componentName,
            By labelLocator, String expectedLabelText,
            By markerLocator, boolean expectedRequired,
            By inputLocator, FieldStyleSpec inputSpec,
            UiFieldState expectedState, UiValidationLevel level) {
        List<UiValidationFailure> failures = new ArrayList<>();
        failures.addAll(validateLabel(labelLocator, componentName, expectedLabelText, FieldStyleSpec.FIELD_LABEL, level));
        failures.addAll(validateRequiredMarker(markerLocator, componentName, expectedRequired, level));
        failures.addAll(validateInputStyle(inputLocator, componentName, inputSpec, expectedState, level));
        failures.addAll(validateFieldState(inputLocator, componentName, expectedState));
        return failures;
    }

    // ── Button ───────────────────────────────────────────────────────────

    public static List<UiValidationFailure> validateButton(By buttonLocator, String componentName,
            String expectedText, boolean expectedEnabled, UiValidationLevel level) {
        return validateButton(buttonLocator, componentName, expectedText, expectedEnabled, FieldStyleSpec.BUTTON, level);
    }

    /** {@code spec}-selecting overload (e.g. {@link FieldStyleSpec#GRID_ROW_BUTTON}). Enabled state
     *  honours both signalling styles: {@code aria-disabled="true"} (ExtJS anchors) and the native
     *  {@code disabled} attribute (plain {@code <button>}s such as the in-grid row buttons). */
    public static List<UiValidationFailure> validateButton(By buttonLocator, String componentName,
            String expectedText, boolean expectedEnabled, FieldStyleSpec spec, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement button = WaitUtils.untilVisible(buttonLocator);
            String actualText = SeleniumUtils.getAttribute(button, "textContent", componentName + "_TEXT");
            checkText(failures, componentName, "button text", expectedText, actualText);

            String ariaDisabled = SeleniumUtils.getAttribute(button, "aria-disabled", componentName + "_ARIA_DISABLED");
            String nativeDisabled = SeleniumUtils.getAttribute(button, "disabled", componentName + "_DISABLED");
            boolean actualEnabled = !"true".equalsIgnoreCase(ariaDisabled) && nativeDisabled == null;
            UiValidationFailure.Status enabledStatus = actualEnabled == expectedEnabled
                    ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL;
            failures.add(new UiValidationFailure(componentName, "enabled state",
                    String.valueOf(expectedEnabled), String.valueOf(actualEnabled), enabledStatus));
            if (level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(button, componentName, spec));
            }
            return failures;
        });
    }

    /** Icon-only button (toolbar buttons have no text): presence, and — when
     *  {@code expectedEnabled} is non-null — enabled state ({@code aria-disabled}/native
     *  {@code disabled}/{@code x-btn-disabled}). {@code null} skips the state check (e.g. Delete Row,
     *  whose state depends on the grid's selection). */
    public static List<UiValidationFailure> validateIconButton(By buttonLocator, String componentName,
            Boolean expectedEnabled) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement button = WaitUtils.untilVisible(buttonLocator);
            failures.add(new UiValidationFailure(componentName, "element presence", "present", "present",
                    UiValidationFailure.Status.PASS));
            if (expectedEnabled != null) {
                String ariaDisabled = SeleniumUtils.getAttribute(button, "aria-disabled", componentName + "_ARIA_DISABLED");
                String nativeDisabled = SeleniumUtils.getAttribute(button, "disabled", componentName + "_DISABLED");
                String cls = SeleniumUtils.getAttribute(button, "class", componentName + "_CLASS");
                boolean actualEnabled = !"true".equalsIgnoreCase(ariaDisabled) && nativeDisabled == null
                        && (cls == null || !cls.contains("x-btn-disabled"));
                failures.add(new UiValidationFailure(componentName, "enabled state",
                        String.valueOf(expectedEnabled), String.valueOf(actualEnabled),
                        actualEnabled == expectedEnabled
                                ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            }
            return failures;
        });
    }

    // ── Section header ───────────────────────────────────────────────────

    public static List<UiValidationFailure> validateSectionHeader(By headerLocator, String componentName,
            String expectedText, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement header = WaitUtils.untilVisible(headerLocator);
            String actualText = SeleniumUtils.getAttribute(header, "textContent", componentName + "_TEXT");
            checkText(failures, componentName, "section header text", expectedText, actualText);
            if (level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(header, componentName, FieldStyleSpec.SECTION_HEADER));
            }
            return failures;
        });
    }

    // ── Status indicator ─────────────────────────────────────────────────

    public static List<UiValidationFailure> validateStatusIndicator(By statusLocator, String componentName,
            String expectedText, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement status = WaitUtils.untilVisible(statusLocator);
            String actualText = SeleniumUtils.getAttribute(status, "textContent", componentName + "_TEXT");
            checkText(failures, componentName, "status text", expectedText, actualText);
            if (level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(status, componentName, FieldStyleSpec.STATUS_BADGE));
            }
            return failures;
        });
    }

    // ── Element absence ──────────────────────────────────────────────────

    /** Asserts a locator is CORRECTLY absent (e.g. a value that only renders once some
     *  prerequisite exists) — the inverse of every other validator here, which all expect presence.
     *  Uses a short poll ({@code SeleniumUtils#isElementPresent}, not {@code WaitUtils#untilVisible}'s
     *  full timeout) since absence is the expected outcome, not a failure to wait out. Fails only if
     *  the element unexpectedly IS present — e.g. a regression that renders a value before its
     *  real prerequisite is met. */
    public static List<UiValidationFailure> validateElementAbsent(By locator, String componentName) {
        return guarded(componentName, () -> {
            boolean present = quickPresent(locator);
            UiValidationFailure.Status status = present
                    ? UiValidationFailure.Status.FAIL : UiValidationFailure.Status.PASS;
            return List.of(new UiValidationFailure(componentName, "element presence",
                    "element NOT present (prerequisite not yet met)",
                    present ? "element found" : "element NOT present (prerequisite not yet met)", status));
        });
    }

    // ── Read-only amount/display field (Balance, Net Amount) ────────────

    public static List<UiValidationFailure> validateReadonlyAmount(By valueLocator, String componentName,
            UiValidationLevel level) {
        return validateReadonlyAmount(valueLocator, componentName, FieldStyleSpec.READONLY_AMOUNT, level);
    }

    /** {@code spec}-selecting overload — lets callers whose readonly amount is styled as an
     *  emphasized {@link FieldStyleSpec#AMOUNT_VALUE_LABEL} (e.g. Net Amount/Payment Amount/
     *  Receipt Amount) opt out of the plain {@link FieldStyleSpec#READONLY_AMOUNT} default (Balance). */
    public static List<UiValidationFailure> validateReadonlyAmount(By valueLocator, String componentName,
            FieldStyleSpec spec, UiValidationLevel level) {
        return validateReadonlyAmount(valueLocator, componentName, spec, null, level);
    }

    /** {@code state}-aware overload — lets a readonly-amount field (e.g. GV's Balance) opt into
     *  the same DISABLED-background override every input field already gets via the {@code state}-
     *  aware {@code checkStyle} overload, for fields that are visually greyed before some
     *  precondition (e.g. Cash/Bank selection) even though they're never directly typed into.
     *  {@code state == null} (or any non-DISABLED value) behaves exactly like the 4-arg overload. */
    public static List<UiValidationFailure> validateReadonlyAmount(By valueLocator, String componentName,
            FieldStyleSpec spec, UiFieldState state, UiValidationLevel level) {
        if (level != UiValidationLevel.CONTRACT) return List.of();
        return guarded(componentName,
                () -> checkStyle(WaitUtils.untilVisible(valueLocator), componentName, spec, state));
    }

    // ── Grid header / cell ───────────────────────────────────────────────

    public static List<UiValidationFailure> validateGridHeader(By headerLocator, String componentName,
            String expectedText, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement header = WaitUtils.untilVisible(headerLocator);
            String actualText = SeleniumUtils.getAttribute(header, "textContent", componentName + "_TEXT");
            // A header container's textContent can include ExtJS's visually-hidden screen-reader text
            // ("Cell value has been edited"); prefer the header's own title span when present.
            List<WebElement> titleSpans = header.findElements(By.cssSelector(".x-column-header-text-inner"));
            if (!titleSpans.isEmpty()) {
                String inner = titleSpans.get(0).getAttribute("textContent");
                if (inner != null && !inner.isBlank()) actualText = inner;
            }
            checkText(failures, componentName, "grid header text", expectedText, actualText);
            if (level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(header, componentName, FieldStyleSpec.GRID_HEADER));
            }
            return failures;
        });
    }

    public static List<UiValidationFailure> validateGridCell(By cellLocator, String componentName,
            boolean numeric, UiValidationLevel level) {
        if (level != UiValidationLevel.CONTRACT) return List.of();
        return guarded(componentName, () -> {
            WebElement cell = WaitUtils.untilVisible(cellLocator);
            return checkStyle(cell, componentName, numeric ? FieldStyleSpec.GRID_CELL_NUMERIC : FieldStyleSpec.GRID_CELL_TEXT);
        });
    }

    /** {@link #validateGridCell} plus a value check: the cell's text must equal
     *  {@code expectedValue} (numeric cells compare as numbers, so "200" equals "200.00").
     *  {@code expectedValue == null} skips the value check (style only). */
    public static List<UiValidationFailure> validateGridCellWithValue(By cellLocator, String componentName,
            boolean numeric, String expectedValue, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement cell = WaitUtils.untilVisible(cellLocator);
            if (expectedValue != null) {
                String actual = SeleniumUtils.getAttribute(cell, "textContent", componentName + "_VALUE");
                boolean match = numeric ? numericEquals(expectedValue, actual)
                        : norm(expectedValue).equalsIgnoreCase(norm(actual));
                failures.add(new UiValidationFailure(componentName, "value", expectedValue, actual,
                        match ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            }
            if (level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(cell, componentName,
                        numeric ? FieldStyleSpec.GRID_CELL_NUMERIC : FieldStyleSpec.GRID_CELL_TEXT));
            }
            return failures;
        });
    }

    /** USER-optional column header: hidden = SKIP ("not displayed"), shown = full header check. */
    public static List<UiValidationFailure> validateGridHeaderOptional(By headerLocator, String componentName,
            String expectedText, UiValidationLevel level) {
        if (!quickPresent(headerLocator)) {
            return skip(componentName, "column presence", "validated if shown", "not displayed");
        }
        return validateGridHeader(headerLocator, componentName, expectedText, level);
    }

    /** USER-optional column cell: hidden = SKIP ("not displayed"), shown = style (+ value if given). */
    public static List<UiValidationFailure> validateGridCellOptional(By cellLocator, String componentName,
            boolean numeric, String expectedValue, UiValidationLevel level) {
        if (!quickPresent(cellLocator)) {
            return skip(componentName, "column presence", "validated if shown", "not displayed");
        }
        return validateGridCellWithValue(cellLocator, componentName, numeric, expectedValue, level);
    }

    /** Asserts an element is correctly ABSENT because of a business rule ({@code reason}, e.g.
     *  "Copy is Add-only"); no-wait lookup. FAILs only when it is unexpectedly displayed. */
    public static List<UiValidationFailure> validateAbsentByDesign(By locator, String componentName, String reason) {
        boolean present = quickPresent(locator);
        return List.of(new UiValidationFailure(componentName, "element presence",
                "NOT present (" + reason + ")",
                present ? "present" : "NOT present (" + reason + ")",
                present ? UiValidationFailure.Status.FAIL : UiValidationFailure.Status.PASS));
    }

    /** No-wait presence check: {@code findElements} once, true only if some match is displayed.
     *  Used for optional/absent-by-design elements, where waiting out a timeout (and triggering the
     *  popup-monitor diagnostics) just to learn "it isn't there" costs seconds per check. */
    public static boolean quickPresent(By locator) {
        try {
            return com.gsl.driver.DriverManager.getDriver().findElements(locator).stream().anyMatch(e -> {
                try {
                    return e.isDisplayed();
                } catch (RuntimeException ex) {
                    return false;
                }
            });
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean numericEquals(String expected, String actual) {
        if (actual == null) return false;
        try {
            return new java.math.BigDecimal(expected.replace(",", "").trim())
                    .compareTo(new java.math.BigDecimal(actual.replace(",", "").trim())) == 0;
        } catch (NumberFormatException e) {
            return expected.trim().equalsIgnoreCase(actual.trim());
        }
    }
}
