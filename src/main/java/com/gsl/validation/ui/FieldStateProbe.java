package com.gsl.validation.ui;

import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.ui.UiFieldState;
import org.openqa.selenium.WebElement;

/**
 * The ONE place that decides whether a form field is disabled / read-only.
 *
 * <p>The app's {@code aria-disabled} mirror is unreliable in BOTH directions: AP's Remarks carried
 * {@code aria-disabled="false"} while natively disabled, and GV's Remarks after Cash/Bank selection carried
 * {@code aria-disabled="true"} while natively ENABLED (white, no {@code disabled} attribute). For real form inputs
 * the truth is therefore the native {@code disabled} attribute or ExtJS's {@code x-item-disabled} class on the
 * field container; ARIA is ignored. Non-input elements (display fields, ExtJS anchor buttons) have no native
 * attribute, so for them ARIA and the container class are the only signals.
 */
public final class FieldStateProbe {

    private FieldStateProbe() {}

    /** Raw signals read from the DOM — kept so a future inconsistency is visible in the report. */
    public record Signals(String tag, boolean nativeDisabled, String ariaDisabled, String ariaReadonly,
                          boolean nativeReadonly, boolean containerDisabled) {
        public String describe() {
            return "native disabled=" + nativeDisabled + ", aria-disabled=" + (ariaDisabled.isEmpty() ? "-" : ariaDisabled)
                    + ", container x-item-disabled=" + containerDisabled;
        }
    }

    private static final String JS =
            "var e=arguments[0];return [e.tagName||'',e.hasAttribute('disabled')?'1':'0',e.getAttribute('aria-disabled')||'',"
                    + "e.getAttribute('aria-readonly')||'',e.hasAttribute('readonly')?'1':'0',"
                    + "e.closest('.x-item-disabled')?'1':'0'].join('|');";

    public static Signals read(WebElement el) {
        String[] p = String.valueOf(JavascriptExecutorUtils.executeScript(JS, el)).split("\\|", -1);
        return new Signals(p[0], "1".equals(p[1]), p[2], p[3], "1".equals(p[4]), "1".equals(p[5]));
    }

    /** Pure decision over the raw signals (unit-tested). */
    public static UiFieldState decide(Signals s) {
        String tag = s.tag() == null ? "" : s.tag().toUpperCase(java.util.Locale.ROOT);
        boolean input = tag.equals("INPUT") || tag.equals("TEXTAREA") || tag.equals("SELECT");
        boolean disabled = input
                ? s.nativeDisabled() || s.containerDisabled()
                : s.nativeDisabled() || s.containerDisabled() || "true".equalsIgnoreCase(s.ariaDisabled());
        if (disabled) return UiFieldState.DISABLED;
        boolean readonly = "true".equalsIgnoreCase(s.ariaReadonly()) || s.nativeReadonly();
        return readonly ? UiFieldState.READONLY : UiFieldState.EDITABLE;
    }

    public static UiFieldState state(WebElement el) {
        return decide(read(el));
    }
}
