package com.gsl.utils.Selenium;

import org.openqa.selenium.By;

public class XpathUtils {

    // ==== XPATH TEMPLATES (private constants) ====
    private static final String ITEM_INFORMATION_TEMPLATE =
            "//label[.//span[normalize-space(text())='%s']]/following-sibling::div" +
                    "//div[contains(@class,'x-form-display-field')]";

    private static final String BATCH_INFORMATION_TEMPLATE =
            "//label[.//span[normalize-space(text())='%s']]/following-sibling::div" +
                    "[contains(@class,'x-form-text-field-body')]";

    // ==== Shared interface for all form label enums ====
    public interface LabelEnum {
        String getLabelText();
        FormSection getSection();
    }

    // ==== Enum to differentiate DOM structures ====
    public static enum FormSection {
        ITEM_INFORMATION,
        BATCH_INFORMATION
    }

    // ==== Dynamic locator builder ====
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
    public static String getFieldValueXpath(LabelEnum label) {
        return getFieldValueBy(label).toString();
    }
}
