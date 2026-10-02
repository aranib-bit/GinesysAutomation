package com.gsl.pages.enums.Finance.Operations.Common;

import org.openqa.selenium.By;

/**
 * The fields of the static "Payment Information" panel shown below the Expense/Income Detail grid
 * (read-only display of the selected row's payment). One place for the on-screen captions (as seen
 * live, trailing colon included) so no caption string is repeated in page code.
 *
 * <p>Not every field is shown for every payment mode / domain (e.g. "Cheque Label" is shown for a
 * Cheque, "Drawn On" is the AR-style caption) — callers treat a missing caption as "not displayed
 * for this mode", not as a failure.
 *
 * <p><b>Scoping.</b> Some captions also exist in Document Information ("Reference:" confirmed live —
 * an unscoped caption xpath returns 2 matches), so every locator anchors on the {@link
 * FormSection#PAYMENT_INFORMATION} title and takes the FIRST caption after it in document order.
 * The single positional {@code [1]} is required for that reason (Document Information precedes
 * Payment Information on the form).
 */
public enum PaymentPanelField {

    PAYMENT_MODE("Payment Mode:"),
    INSTRUMENT_NO("Instrument No.:"),
    INSTRUMENT_DATE("Instrument Date:"),
    CHEQUE_LABEL("Cheque Label:"),
    DRAWN_ON("Drawn On:"),
    REFERENCE("Reference:");

    private final String caption;

    PaymentPanelField(String caption) {
        this.caption = caption;
    }

    /** The exact on-screen caption, e.g. {@code "Instrument No.:"}. */
    public String caption() { return caption; }

    /** Component key used in UI-validation results, e.g. {@code PAYMENT_PANEL_INSTRUMENT_NO}. */
    public String key() { return "PAYMENT_PANEL_" + name(); }

    private String captionSpan(String windowScope) {
        return "(" + windowScope + FormSection.PAYMENT_INFORMATION.titleXpath()
                + "/following::span[normalize-space()='" + caption + "'])[1]";
    }

    /** The caption's {@code <label>} element, under the Payment Information section of {@code windowScope}. */
    public By labelLocator(String windowScope) {
        return By.xpath(captionSpan(windowScope) + "/ancestor::label");
    }

    /** The (disabled) value input next to the caption; read its {@code value} property. */
    public By valueLocator(String windowScope) {
        return By.xpath(captionSpan(windowScope)
                + "/ancestor::label/following-sibling::div//input[contains(@id,'inputEl')]");
    }
}
