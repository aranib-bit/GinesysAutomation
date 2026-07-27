package com.gsl.pages.enums.Finance.Setup;

/**
 * Fixed field-label texts on the "Add Account Class" modal popup — see
 * {@link com.gsl.pages.Finance.Setup.AddAccountClassPage}. Each label is matched against a
 * {@code span}, not a {@code label} element, in the confirmed DOM.
 */
public enum AddAccountClassFieldLabel {

    CLASS_NAME("Class Name:"),
    CLASS_TYPE("Class Type:");

    private final String text;

    AddAccountClassFieldLabel(String text) {
        this.text = text;
    }

    public String text() {
        return text;
    }
}
