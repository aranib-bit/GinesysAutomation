package com.gsl.pages.enums.Procurement.Common;


import com.gsl.utils.Selenium.XpathUtils.FormSection;
import com.gsl.utils.Selenium.XpathUtils.LabelEnum;

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
    public String getXpath() {
        return String.format(TEMPLATE, labelText, role);
    }

    public String getRole() {
        return role;
    }
}
