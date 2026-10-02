package com.gsl.pages.enums.Procurement.Common;


import com.gsl.utils.Selenium.XpathUtils.FormSection;
import com.gsl.utils.Selenium.XpathUtils.LabelEnum;

/**
 * Field labels on a Procurement popup's editable "Batch Information" panel (Batch No/Mfg. Date
 * input fields) — implements {@link LabelEnum} ({@link #getSection()} always returns {@link
 * FormSection#BATCH_INFORMATION}), but unlike {@link ItemInformationDetailsFormLabel} this enum
 * does NOT go through {@code XpathUtils.getFieldValueBy(LabelEnum)} for its own {@link
 * #getXpath()} — it builds its own input-field locator (keyed by {@link #getRole()}'s ExtJS
 * {@code role} attribute: textbox/combobox) via its own {@code TEMPLATE}, since these are live
 * input fields rather than the read-only display-value {@code div}s {@code XpathUtils} targets.
 * <p>TODO-confirm: no current caller of this enum was found anywhere in the codebase — it does
 * not yet appear to be wired into a page object's Batch Information popup handling.
 */
public enum BatchDetailsFormLabel implements LabelEnum {
    BATCH_NO("Batch No:", "textbox"),
    MFG_DATE("Mfg. Date:", "combobox");

    private static final String TEMPLATE =
            "//span[normalize-space(text())='%s']/ancestor::div[contains(@id,'outerCt')]//input[@role='%s']";

    private final String labelText;
    private final String role;

    BatchDetailsFormLabel(String labelText,String role) {
        this.labelText = labelText;
        this.role = role;
    }

    @Override
    public String getLabelText() {
        return labelText;
    }

    @Override
    public FormSection getSection() {
        return FormSection.BATCH_INFORMATION;  // pick correct section
    }

    // --- your custom helper (not part of LabelEnum) ---
    /** @return locator xpath for this field's live input element, matched by label text + ExtJS {@code role}. */
    public String getXpath() {
        return String.format(TEMPLATE, labelText, role);
    }

    /** @return this field's ExtJS input {@code role} attribute, e.g. "textbox" or "combobox". */
    public String getRole() {
        return role;
    }
}
