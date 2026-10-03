package com.gsl.validation.ui;

import com.gsl.driver.DriverManager;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WrapsDriver;

import java.util.regex.Pattern;

/**
 * One confirmed, already-tracked UI defect that must stay VISIBLE in the report without failing
 * the test — the UI counterpart of the DB snapshot validator's {@code ColumnRule.knownIssue}.
 *
 * <p>An entry matches a failing check on {@code component} + {@code property} AND the exact
 * {@code actual} value: a DIFFERENT wrong value is a new problem and still FAILS, so a known issue
 * can never mask drift. Register entries in {@link UiKnownIssues}.
 *
 * @param component the result's component key, e.g. {@code FOOTER_BUTTON_COPY}
 * @param property  the checked property, e.g. {@code background-color}
 * @param actual    the exact observed value that is the known (wrong) behaviour
 * @param note      why it is known / what is awaited, shown beside the amber row
 * @param platform  browser platform (e.g. {@code linux}) the issue exists on, or {@code null} for
 *                  every platform; elsewhere the entry neither matches nor is reported as stale
 */
public record UiKnownIssue(String component, String property, String actual, String note, String platform) {

    /** Platform-independent entry. */
    public UiKnownIssue(String component, String property, String actual, String note) {
        this(component, property, actual, note, null);
    }

    /** {@code component} may be a regex (e.g. {@code GRID_ROW_BUTTON_SITE_ROW_\d+}) for per-row keys. */
    boolean matches(String component, String property, String actual) {
        return sameCheck(component, property) && this.actual.equals(actual == null ? "" : actual.trim());
    }

    boolean sameCheck(String component, String property) {
        return appliesHere()
                && Pattern.compile(this.component, Pattern.CASE_INSENSITIVE)
                        .matcher(component == null ? "" : component).matches()
                && this.property.equalsIgnoreCase(property == null ? "" : property);
    }

    private boolean appliesHere() {
        if (platform == null) return true;
        try {
            WebDriver d = DriverManager.getDriver();
            while (d instanceof WrapsDriver w) d = w.getWrappedDriver();
            if (d instanceof HasCapabilities hc) {
                Capabilities c = hc.getCapabilities();
                return c.getPlatformName() != null
                        && c.getPlatformName().toString().equalsIgnoreCase(platform);
            }
        } catch (Exception ignored) {
            // no driver / capabilities unreadable: treat as not applicable rather than mask a failure
        }
        return false;
    }
}
