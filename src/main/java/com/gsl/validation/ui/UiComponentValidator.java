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

    /** First match of {@code by} that Selenium reports as displayed. A label or field can exist several times on a
     *  form with all but one copy permanently hidden (e.g. three "Voucher No.:" on an Edit form); waiting on the
     *  first DOM match - what a plain visibility wait does - then times out on a hidden copy. Deliberately NOT
     *  viewport-bound: an element that is displayed but scrolled out of view (the Payment Information panel
     *  below the grid) is still "visible" here, as it is for every other check. */
    private static WebElement visible(By by) {
        long seconds = com.gsl.config.ConfigFactory.getConfig().timeout();
        org.openqa.selenium.support.ui.WebDriverWait wait = new org.openqa.selenium.support.ui.WebDriverWait(
                com.gsl.driver.DriverManager.getDriver(), java.time.Duration.ofSeconds(seconds),
                java.time.Duration.ofMillis(com.gsl.config.ConfigFactory.getWaitConfig().pollingMillis()));
        try {
            return wait.until(d -> firstMatch(d.findElements(by), UiComponentValidator::isDisplayedSafely));
        } catch (org.openqa.selenium.TimeoutException e) {
            throw new org.openqa.selenium.NoSuchElementException("No displayed element for " + by + " within " + seconds + "s", e);
        }
    }

    private static boolean isDisplayedSafely(WebElement el) {
        try {
            return el.isDisplayed();
        } catch (org.openqa.selenium.StaleElementReferenceException e) {
            return false;
        }
    }

    /** First item satisfying {@code displayed}, or {@code null}. Pure logic, unit-tested. */
    public static <T> T firstMatch(List<T> items, java.util.function.Predicate<T> displayed) {
        for (T item : items) {
            if (displayed.test(item)) return item;
        }
        return null;
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
            WebElement label = visible(labelLocator);
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
            boolean present = quickPresent(markerLocator);
            UiValidationFailure.Status presenceStatus = present == expectedRequired
                    ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL;
            failures.add(new UiValidationFailure(componentName + "_UI_MANDATORY", "mandatory",
                    expectedRequired ? "Mandatory (*)" : "Non-mandatory", present ? "Mandatory (*)" : "Non-mandatory",
                    presenceStatus));
            if (present && level == UiValidationLevel.CONTRACT) {
                failures.addAll(checkStyle(visible(markerLocator),
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
            WebElement icon = visible(section.chevronLocator(scopeXpath));
            String cls = SeleniumUtils.getAttribute(icon, "class", component + "_CHEVRON");
            String actual = cls != null && cls.contains(FormSection.CHEVRON_OPEN_CLASS) ? "OPEN"
                    : cls != null && cls.contains(FormSection.CHEVRON_CLOSED_CLASS) ? "CLOSED"
                    : "UNKNOWN (" + cls + ")";
            String expected = expectedOpen ? "OPEN" : "CLOSED";
            return List.of(new UiValidationFailure(component, "section state", expected, actual,
                    expected.equals(actual) ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        });
    }

    /** Current section state as text ("OPEN" / "CLOSED" / "UNKNOWN (...)" / "NOT PRESENT") without asserting. */
    public static String readSectionState(FormSection section, String scopeXpath) {
        try {
            if (!quickPresent(section.chevronLocator(scopeXpath))) return "NOT PRESENT";
            WebElement icon = visible(section.chevronLocator(scopeXpath));
            String cls = icon.getAttribute("class");
            return cls != null && cls.contains(FormSection.CHEVRON_OPEN_CLASS) ? "OPEN"
                    : cls != null && cls.contains(FormSection.CHEVRON_CLOSED_CLASS) ? "CLOSED"
                    : "UNKNOWN (" + cls + ")";
        } catch (RuntimeException e) {
            return "NOT PRESENT";
        }
    }

    /** Records (does not assert) a section's state — PASS informational row, state shown in the node badge. */
    public static List<UiValidationFailure> recordSectionState(FormSection section, String scopeXpath) {
        return List.of(new UiValidationFailure("SECTION_" + section.key(), "section state", "(recorded)",
                readSectionState(section, scopeXpath), UiValidationFailure.Status.PASS));
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
            WebElement el = visible(locator);
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
            WebElement el = visible(locator);
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
            WebElement input = visible(inputLocator);
            FieldStateProbe.Signals signals = FieldStateProbe.read(input);
            UiFieldState actualState = FieldStateProbe.decide(signals);
            failures.add(new UiValidationFailure(componentName, "field state",
                    expectedState.name(), actualState.name(),
                    actualState == expectedState ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            // The raw signals stay visible: the app's ARIA mirror is unreliable (see FieldStateProbe), so a
            // disagreement between ARIA and the native state is shown, never silently resolved.
            failures.add(new UiValidationFailure(componentName, "field state signals", "(recorded)",
                    signals.describe(), UiValidationFailure.Status.PASS));
            return failures;
        });
    }

    /** The field must not be editable — DISABLED or READONLY both pass; the actual state is shown.
     *  Used where the business rule is "never editable" and the app signals it either way (e.g.
     *  Cash/Bank Balance before and after Cash/Bank selection). */
    public static List<UiValidationFailure> validateNotEditable(By inputLocator, String componentName) {
        return guarded(componentName, () -> {
            UiFieldState actual = FieldStateProbe.state(visible(inputLocator));
            return List.of(new UiValidationFailure(componentName, "field state", "NOT EDITABLE (disabled/readonly)",
                    actual.name(), actual == UiFieldState.EDITABLE
                            ? UiValidationFailure.Status.FAIL : UiValidationFailure.Status.PASS));
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
                () -> checkStyle(visible(inputLocator), componentName, spec, expectedState));
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
        failures.addAll(validateElementTypeBaselined(inputLocator, componentName));
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
            WebElement button = visible(buttonLocator);
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

    /** A button that may or may not exist for a voucher: absent → SKIP "not present"; present → element
     *  type, text, enabled state (asserted only when the baseline's {@code EnabledAfterFill} says
     *  ENABLED/DISABLED, otherwise recorded) and, at CONTRACT level, style against {@code spec}. */
    public static List<UiValidationFailure> validateButtonIfPresent(By buttonLocator, String componentName,
            String expectedText, FieldStyleSpec spec, UiValidationLevel level) {
        if (!quickPresent(buttonLocator)) {
            return skip(componentName, "element presence", "validated if present", "not present");
        }
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement button = visible(buttonLocator);
            failures.addAll(validateElementTypeBaselined(buttonLocator, componentName));
            failures.add(new UiValidationFailure(componentName, "element presence", "present", "present",
                    UiValidationFailure.Status.PASS));
            checkText(failures, componentName, "button text", expectedText,
                    SeleniumUtils.getAttribute(button, "textContent", componentName + "_TEXT"));
            String ariaDisabled = SeleniumUtils.getAttribute(button, "aria-disabled", componentName + "_ARIA_DISABLED");
            String nativeDisabled = SeleniumUtils.getAttribute(button, "disabled", componentName + "_DISABLED");
            boolean enabled = !"true".equalsIgnoreCase(ariaDisabled) && nativeDisabled == null;
            String raw = UiStyleBaseline.attribute(componentName, UiStyleBaseline.ENABLED_AFTER_FILL);
            if (raw != null) {
                boolean expectEnabled = raw.trim().equalsIgnoreCase("ENABLED") || raw.trim().equalsIgnoreCase("EDITABLE");
                failures.add(new UiValidationFailure(componentName, "enabled state",
                        String.valueOf(expectEnabled), String.valueOf(enabled),
                        enabled == expectEnabled ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
            } else {
                failures.add(new UiValidationFailure(componentName, "enabled state", "(recorded)",
                        String.valueOf(enabled), UiValidationFailure.Status.PASS));
            }
            if (level == UiValidationLevel.CONTRACT) failures.addAll(checkStyle(button, componentName, spec));
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
            WebElement button = visible(buttonLocator);
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
            WebElement header = visible(headerLocator);
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
            WebElement status = visible(statusLocator);
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
                () -> checkStyle(visible(valueLocator), componentName, spec, state));
    }

    // ── Grid header / cell ───────────────────────────────────────────────

    public static List<UiValidationFailure> validateGridHeader(By headerLocator, String componentName,
            String expectedText, UiValidationLevel level) {
        return guarded(componentName, () -> {
            List<UiValidationFailure> failures = new ArrayList<>();
            WebElement header = visible(headerLocator);
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
            WebElement cell = visible(cellLocator);
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
            WebElement cell = visible(cellLocator);
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

    // ── Element type ─────────────────────────────────────────────────────

    /** Reads tag, input type and a class string for type detection. The trigger classes (date picker,
     *  combo arrow, lookup picker) live on SIBLING elements of the input inside its field container, so
     *  the container's descendants and the component ids are folded into synthetic tokens. */
    private static final String ELEMENT_INFO_JS =
            "var e=arguments[0];var a=e.closest('.x-field,.x-btn,.x-grid,.x-boundlist,.x-form-item');"
            + "var c=(e.className||'')+' '+(a?a.className:'');"
            + "var ids=((e.id||'')+' '+(a?a.id:'')+' '+(e.getAttribute('data-componentid')||'')).toLowerCase();"
            + "if(a){"
            + "if(a.querySelector('.x-form-date-trigger'))c+=' x-form-date-trigger';"
            + "if(a.querySelector('.x-form-arrow-trigger'))c+=' x-form-arrow-trigger';"
            + "if(a.querySelector('.x-form-search-trigger'))c+=' search-trigger';"
            + "if(a.querySelector('.x-form-spinner-up'))c+=' x-form-type-number';}"
            + "if(ids.indexOf('datefield')>=0)c+=' x-form-date-trigger';"
            + "if(ids.indexOf('picklist')>=0)c+=' search-trigger';"
            + "if(ids.indexOf('combo')>=0)c+=' x-form-arrow-trigger';"
            + "return (e.tagName||'')+'|'+(e.getAttribute('type')||'')+'|'+c;";

    /** Detects the control type of the element at {@code locator} (see {@link UiElementType#detect}). */
    public static UiElementType detectElementType(WebElement element) {
        String[] info = String.valueOf(com.gsl.utils.Selenium.JavascriptExecutorUtils.executeScript(ELEMENT_INFO_JS, element))
                .split("\\|", 3);
        return UiElementType.detect(info[0], info.length > 1 ? info[1] : "", info.length > 2 ? info[2] : "");
    }

    /** One "element type" row: expected (from the baseline) vs the type detected from the DOM.
     *  {@code expected == null} records nothing (not baselined). */
    public static List<UiValidationFailure> validateElementType(By locator, String componentName, UiElementType expected) {
        return expected == null ? List.of() : validateElementType(locator, componentName, List.of(expected));
    }

    /** Passes when the detected type is any of {@code allowed} (e.g. "Text Field or Dropdown"). */
    public static List<UiValidationFailure> validateElementType(By locator, String componentName,
            List<UiElementType> allowed) {
        if (allowed == null || allowed.isEmpty()) return List.of();
        return guarded(componentName, () -> {
            UiElementType actual = detectElementType(visible(locator));
            String expectedText = allowed.stream().map(UiElementType::label).collect(java.util.stream.Collectors.joining(" or "));
            return List.of(new UiValidationFailure(componentName, "element type", expectedText, actual.label(),
                    allowed.contains(actual) ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        });
    }

    /** Element-type row driven by the Excel baseline ({@code ElementType} column of the component's row).
     *  Not baselined → a visible SKIP that still shows the type detected from the DOM, so the first
     *  live run tells the maintainer exactly what to enter in the workbook. */
    public static List<UiValidationFailure> validateElementTypeBaselined(By locator, String componentName) {
        List<UiElementType> expected;
        try {
            expected = UiElementType.parseAll(UiStyleBaseline.attribute(componentName, UiStyleBaseline.ELEMENT_TYPE));
        } catch (IllegalArgumentException e) {
            return List.of(new UiValidationFailure(componentName, "element type", e.getMessage(), "invalid baseline"));
        }
        if (!expected.isEmpty()) return validateElementType(locator, componentName, expected);
        return guarded(componentName, () -> skip(componentName, "element type", "defined in Excel (ElementType)",
                "not baselined — detected: " + detectElementType(visible(locator)).label()));
    }

    // ── Baselined field (popups and any field whose expectations live in the workbook) ─────────

    /** Observed text/value of an input or display element, trimmed ({@code ""} when absent). */
    public static String readValue(By locator) {
        try {
            if (!quickPresent(locator)) return "";
            WebElement el = visible(locator);
            String v = el.getAttribute("value");
            if (v == null || v.isBlank()) v = el.getAttribute("textContent");
            return v == null ? "" : norm(v);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /**
     * The full property set of one field whose expectations come from the workbook: label text + style,
     * element type, mandatory marker, field state, value and input style. Every check the workbook does
     * not (yet) define is a visible SKIP / "recorded" row that shows the observed value, so the first live
     * run tells the maintainer exactly what to enter. Absent field -> one SKIP "not displayed".
     *
     * @param stateKey baseline column for the expected state ({@code UiStyleBaseline.ENABLED_BEFORE_FILL} /
     *                 {@code ENABLED_AFTER_FILL}), {@code null} to only record it
     */
    public static List<UiValidationFailure> validateFieldBaselined(String component, By labelLocator, String labelText,
            By markerLocator, By inputLocator, FieldStyleSpec spec, String stateKey, UiValidationLevel level) {
        if (!quickPresent(inputLocator)) {
            return skip(component, "element presence", "validated if shown", "not displayed");
        }
        List<UiValidationFailure> f = new ArrayList<>();
        f.addAll(validateLabel(labelLocator, component, labelText, FieldStyleSpec.FIELD_LABEL, level));
        f.addAll(validateElementTypeBaselined(inputLocator, component));

        String mandatory = UiStyleBaseline.attribute(component, UiStyleBaseline.MANDATORY);
        boolean marked = quickPresent(markerLocator);
        if (mandatory != null) {
            String m = mandatory.trim().toLowerCase(java.util.Locale.ROOT);
            boolean expected = m.equals("yes") || m.equals("true") || m.equals("y") || m.equals("mandatory");
            f.add(new UiValidationFailure(component, "mandatory", expected ? "Mandatory (*)" : "Non-mandatory",
                    marked ? "Mandatory (*)" : "Non-mandatory",
                    marked == expected ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        } else {
            f.addAll(skip(component, "mandatory", "defined in Excel (Mandatory)",
                    "not baselined - observed: " + (marked ? "Mandatory (*)" : "Non-mandatory")));
        }

        FieldSnapshot snap = captureField(inputLocator);
        UiFieldState expectedState = stateKey == null ? null : baselineState(component, stateKey);
        if (expectedState != null) {
            f.add(new UiValidationFailure(component, "field state", expectedState.name(), snap.stateText(),
                    snap.state() == expectedState ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        } else {
            f.add(new UiValidationFailure(component, "field state", "(recorded)", snap.stateText(),
                    UiValidationFailure.Status.PASS));
        }
        f.add(new UiValidationFailure(component, "value", "(recorded)", snap.valueText(), UiValidationFailure.Status.PASS));
        if (level == UiValidationLevel.CONTRACT && spec != null) {
            f.addAll(validateInputStyle(inputLocator, component, spec,
                    expectedState == null ? snap.state() : expectedState, level));
        }
        return f;
    }

    /** A value that must equal {@code expected} (trimmed, case-insensitive); one PASS/FAIL row. */
    public static List<UiValidationFailure> validateEquals(String component, String property, String expected, String actual) {
        boolean ok = norm(expected).equalsIgnoreCase(norm(actual));
        return List.of(new UiValidationFailure(component, property, expected, actual,
                ok ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
    }

    /** Thousands-separated number with 2 decimals and an optional Cr/Dr suffix, e.g. {@code 780,142.00 Cr}. */
    private static final java.util.regex.Pattern BALANCE_FORMAT =
            java.util.regex.Pattern.compile("^-?\\d{1,3}(,\\d{3})*(\\.\\d{2})?(\\s?(Cr|Dr))?$");

    public static List<UiValidationFailure> validateBalanceFormat(String component, String actual) {
        boolean ok = actual != null && BALANCE_FORMAT.matcher(norm(actual)).matches();
        return List.of(new UiValidationFailure(component, "value format",
                "number, thousands commas, 2 decimals, optional Cr/Dr", actual == null || actual.isBlank() ? "(empty)" : actual,
                ok ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
    }

    // ── Before / after field state ───────────────────────────────────────

    /** What a field looked like at one checkpoint. */
    public record FieldSnapshot(boolean present, UiFieldState state, String value) {
        static FieldSnapshot absent() { return new FieldSnapshot(false, null, null); }
        public String stateText() { return !present ? "NOT PRESENT" : state.name(); }
        public String valueText() { return !present ? "NOT PRESENT" : (value == null || value.isBlank() ? "(empty)" : value); }
    }

    /** Reads presence, enabled/read-only state and value without failing when the field is absent. */
    public static FieldSnapshot captureField(By locator) {
        if (!quickPresent(locator)) return FieldSnapshot.absent();
        try {
            WebElement el = visible(locator);
            UiFieldState state = FieldStateProbe.state(el);
            String value = el.getAttribute("value");
            if (value == null || value.isBlank()) value = el.getAttribute("textContent");
            return new FieldSnapshot(true, state, value == null ? "" : norm(value));
        } catch (RuntimeException e) {
            return FieldSnapshot.absent();
        }
    }

    /**
     * Before/after report rows for one field: state before / after (vs expected, when given), whether
     * the state changed (vs whether it was expected to), and value before / after / changed.
     *
     * @param expectedBefore state expected at the first checkpoint, or null (not checked)
     * @param expectedAfter  state expected after filling, or null (not checked)
     * @param expectValueChange true/false when the value must/must not change; null = informational only
     * @param expectedAfterValue exact value expected after filling, or null
     */
    public static List<UiValidationFailure> validateFieldBeforeAfter(String componentName,
            FieldSnapshot before, FieldSnapshot after, UiFieldState expectedBefore, UiFieldState expectedAfter,
            Boolean expectValueChange, String expectedAfterValue) {
        return validateFieldBeforeAfter(componentName, "fill", before, after, expectedBefore, expectedAfter,
                expectValueChange, expectedAfterValue);
    }

    /** As above; {@code action} names what happened between the checkpoints ("fill", "line selection"). */
    public static List<UiValidationFailure> validateFieldBeforeAfter(String componentName, String action,
            FieldSnapshot before, FieldSnapshot after, UiFieldState expectedBefore, UiFieldState expectedAfter,
            Boolean expectValueChange, String expectedAfterValue) {
        List<UiValidationFailure> r = new ArrayList<>();
        if (!before.present() || !after.present()) {
            r.add(new UiValidationFailure(componentName, "element presence", "present at both checkpoints",
                    "before: " + before.stateText() + ", after: " + after.stateText()));
            return r;
        }
        if (expectedBefore != null) {
            r.add(new UiValidationFailure(componentName, "state before " + action, expectedBefore.name(), before.stateText(),
                    before.state() == expectedBefore ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        }
        if (expectedAfter != null) {
            r.add(new UiValidationFailure(componentName, "state after " + action, expectedAfter.name(), after.stateText(),
                    after.state() == expectedAfter ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        }
        boolean stateChanged = before.state() != after.state();
        if (expectedBefore != null && expectedAfter != null) {
            boolean expectChange = expectedBefore != expectedAfter;
            r.add(new UiValidationFailure(componentName, "state change on " + action,
                    expectChange ? "changes " + expectedBefore + " → " + expectedAfter : "unchanged (" + expectedBefore + ")",
                    stateChanged ? "changed " + before.stateText() + " → " + after.stateText() : "unchanged (" + after.stateText() + ")",
                    stateChanged == expectChange ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        }
        r.add(new UiValidationFailure(componentName, "value before " + action, "(recorded)", before.valueText(),
                UiValidationFailure.Status.PASS));
        if (expectedAfterValue != null) {
            r.add(new UiValidationFailure(componentName, "value after " + action, expectedAfterValue, after.valueText(),
                    norm(expectedAfterValue).equalsIgnoreCase(norm(after.value()))
                            ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        } else {
            r.add(new UiValidationFailure(componentName, "value after " + action, "(recorded)", after.valueText(),
                    UiValidationFailure.Status.PASS));
        }
        if (expectValueChange != null) {
            boolean changed = !norm(before.value()).equals(norm(after.value()));
            r.add(new UiValidationFailure(componentName, "value change on " + action,
                    expectValueChange ? "changes" : "unchanged", changed ? "changed" : "unchanged",
                    changed == expectValueChange ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        }
        return r;
    }

    /** {@link #validateFieldBeforeAfter} with the expected before/after states taken from the Excel
     *  baseline ({@code EnabledBeforeFill} / {@code EnabledAfterFill}: EDITABLE, READONLY, DISABLED;
     *  blank = not asserted, just recorded). */
    public static List<UiValidationFailure> validateFieldBeforeAfterBaselined(String componentName,
            FieldSnapshot before, FieldSnapshot after, Boolean expectValueChange, String expectedAfterValue) {
        return validateFieldBeforeAfterBaselined(componentName, "fill", before, after, expectValueChange, expectedAfterValue);
    }

    public static List<UiValidationFailure> validateFieldBeforeAfterBaselined(String componentName, String action,
            FieldSnapshot before, FieldSnapshot after, Boolean expectValueChange, String expectedAfterValue) {
        return validateFieldBeforeAfter(componentName, action, before, after,
                baselineState(componentName, UiStyleBaseline.ENABLED_BEFORE_FILL),
                baselineState(componentName, UiStyleBaseline.ENABLED_AFTER_FILL),
                expectValueChange, expectedAfterValue);
    }

    /** Expected field state from the baseline column {@code key}, or null when not baselined. */
    public static UiFieldState baselineStateOf(String component, String key) {
        return baselineState(component, key);
    }

    private static UiFieldState baselineState(String component, String key) {
        String raw = UiStyleBaseline.attribute(component, key);
        if (raw == null) return null;
        String v = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (v) {
            case "ENABLED", "EDITABLE" -> UiFieldState.EDITABLE;
            case "DISABLED" -> UiFieldState.DISABLED;
            case "READONLY", "READ-ONLY", "READ_ONLY" -> UiFieldState.READONLY;
            default -> throw new IllegalArgumentException("Unknown " + key + " '" + raw + "' for " + component + " in baseline");
        };
    }

    // ── Grid column mandatory / non-mandatory ───────────────────────────

    /** The header must show the required marker ({@code *} in its text, or a descendant whose class
     *  contains required/mandatory) exactly when {@code expectedMandatory}. Reports "mandatory" /
     *  "non-mandatory" so a reader sees the column classification, not a boolean. */
    public static List<UiValidationFailure> validateGridColumnMandatory(By headerLocator, String componentName,
            boolean expectedMandatory) {
        return guarded(componentName, () -> {
            WebElement header = visible(headerLocator);
            Object marked = com.gsl.utils.Selenium.JavascriptExecutorUtils.executeScript(
                    "var h=arguments[0];return (h.textContent||'').indexOf('*')>=0"
                            + "||!!h.querySelector('[class*=required],[class*=mandatory]');", header);
            boolean actual = Boolean.TRUE.equals(marked);
            return List.of(new UiValidationFailure(componentName, "mandatory",
                    expectedMandatory ? "Mandatory (*)" : "Non-mandatory", actual ? "Mandatory (*)" : "Non-mandatory",
                    actual == expectedMandatory ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        });
    }

    /** Mandatory check whose expectation comes from the baseline's {@code Mandatory} column
     *  (Yes/No/true/false); not baselined → SKIP showing the observed classification. */
    public static List<UiValidationFailure> validateGridColumnMandatoryBaselined(By headerLocator, String componentName) {
        String raw = UiStyleBaseline.attribute(componentName, UiStyleBaseline.MANDATORY);
        if (raw != null) {
            String v = raw.trim().toLowerCase(java.util.Locale.ROOT);
            return validateGridColumnMandatory(headerLocator, componentName,
                    v.equals("yes") || v.equals("true") || v.equals("y") || v.equals("mandatory"));
        }
        return guarded(componentName, () -> {
            Object marked = com.gsl.utils.Selenium.JavascriptExecutorUtils.executeScript(
                    "var h=arguments[0];return (h.textContent||'').indexOf('*')>=0"
                            + "||!!h.querySelector('[class*=required],[class*=mandatory]');",
                    visible(headerLocator));
            return skip(componentName, "mandatory", "defined in Excel (Mandatory)",
                    "not baselined — observed: " + (Boolean.TRUE.equals(marked) ? "Mandatory (*)" : "Non-mandatory"));
        });
    }

    // ── Section state across an action ──────────────────────────────────

    /** Section state recorded at two points around an action: initial, final, and whether it changed
     *  as expected. {@code expectedInitialOpen}/{@code expectedFinalOpen} state the business rule. */
    public static List<UiValidationFailure> validateSectionStateChange(String sectionKey, String action,
            String actualInitial, boolean expectedInitialOpen, String actualFinal, boolean expectedFinalOpen) {
        String component = "SECTION_" + sectionKey;
        String ei = expectedInitialOpen ? "OPEN" : "CLOSED";
        String ef = expectedFinalOpen ? "OPEN" : "CLOSED";
        List<UiValidationFailure> r = new ArrayList<>();
        r.add(new UiValidationFailure(component, "section state before " + action, ei, actualInitial,
                ei.equals(actualInitial) ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        r.add(new UiValidationFailure(component, "section state after " + action, ef, actualFinal,
                ef.equals(actualFinal) ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
        return r;
    }

    // ── Count pair (Record Count / Total Count) ─────────────────────────

    /** Before/after numeric count with the expected delta ("Record Count: 0 → 1, expected +1"). */
    public static List<UiValidationFailure> validateCountPair(String componentName, String label,
            Integer before, Integer after, int expectedDelta) {
        if (before == null || after == null) {
            return List.of(new UiValidationFailure(componentName, label + " change",
                    (expectedDelta >= 0 ? "+" : "") + expectedDelta, "could not read (before=" + before + ", after=" + after + ")"));
        }
        int actualDelta = after - before;
        return List.of(new UiValidationFailure(componentName, label + " change",
                before + " → " + (before + expectedDelta) + " (" + (expectedDelta >= 0 ? "+" : "") + expectedDelta + ")",
                before + " → " + after + " (" + (actualDelta >= 0 ? "+" : "") + actualDelta + ")",
                actualDelta == expectedDelta ? UiValidationFailure.Status.PASS : UiValidationFailure.Status.FAIL));
    }

    public static boolean numericEqualsPublic(String expected, String actual) {
        return numericEquals(expected, actual);
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
