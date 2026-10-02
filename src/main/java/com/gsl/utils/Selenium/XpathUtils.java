package com.gsl.utils.Selenium;

import org.openqa.selenium.By;

/**
 * Builds {@link By} xpath locators for read-only field-value display elements identified by
 * their visible label text, across the two DOM shapes used by Item Information and Batch
 * Information panels. Callers implement {@link LabelEnum} on their own field enums so label text
 * and section stay typed and centralized instead of scattered as magic strings/xpaths across
 * page objects.
 */
public class XpathUtils {

    // ==== XPATH TEMPLATES (private constants) ====
    private static final String ITEM_INFORMATION_TEMPLATE =
            "//label[.//span[normalize-space(text())='%s']]/following-sibling::div" +
                    "//div[contains(@class,'x-form-display-field')]";

    private static final String BATCH_INFORMATION_TEMPLATE =
            "//label[.//span[normalize-space(text())='%s']]/following-sibling::div" +
                    "[contains(@class,'x-form-text-field-body')]";

    // ==== Shared interface for all form label enums ====
    /** Implement on a field-label enum to resolve that field's display-value locator via {@link #getFieldValueBy}. */
    public interface LabelEnum {
        String getLabelText();
        FormSection getSection();
    }

    // ==== Enum to differentiate DOM structures ====
    /** Which of the two known DOM shapes a label's display value lives in. */
    public static enum FormSection {
        ITEM_INFORMATION,
        BATCH_INFORMATION
    }

    // ==== Dynamic locator builder ====
    /**
     * Builds the display-value locator for {@code label}, using the xpath template matching its
     * {@link FormSection}.
     *
     * @throws IllegalArgumentException if {@code label}'s section isn't one of the known values
     */
    public static By getFieldValueBy(LabelEnum label) {
        String xpath;

        switch (label.getSection()) {
            case ITEM_INFORMATION:
                xpath = String.format(ITEM_INFORMATION_TEMPLATE, label.getLabelText());
                break;

            case BATCH_INFORMATION:
                xpath = String.format(BATCH_INFORMATION_TEMPLATE, label.getLabelText());
                break;

            default:
                throw new IllegalArgumentException("Unsupported section: " + label.getSection());
        }

        return By.xpath(xpath);
    }

    // Optional: return raw XPath string (for debugging/logging)
    /** Same as {@link #getFieldValueBy(LabelEnum)}, returned as the raw xpath string. */
    public static String getFieldValueXpath(LabelEnum label) {
        return getFieldValueBy(label).toString();
    }
}
