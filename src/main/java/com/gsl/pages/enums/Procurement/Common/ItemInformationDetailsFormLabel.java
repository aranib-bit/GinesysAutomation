package com.gsl.pages.enums.Procurement.Common;

import com.gsl.utils.Selenium.XpathUtils;

/**
 * Field labels on a Procurement popup's read-only "Item Information" panel (Item/Description/
 * Quantity display values) — implements {@link XpathUtils.LabelEnum} so {@link
 * XpathUtils#getFieldValueBy(XpathUtils.LabelEnum)} can build each field's display-value locator
 * from the Item Information DOM shape ({@link #getSection()} always returns {@link
 * XpathUtils.FormSection#ITEM_INFORMATION}). Mirrors {@link BatchDetailsFormLabel}'s intent for
 * the Item Information panel instead of Batch Information.
 * <p>TODO-confirm: no current caller of this enum was found anywhere in the codebase — it does
 * not yet appear to be wired into a page object's read of an Item Information popup.
 */
public enum ItemInformationDetailsFormLabel implements XpathUtils.LabelEnum {
    /** Label text is "Item:" — despite the constant name, this reads the Item Code/Barcode display value. */
    ITEM_ICODE_BARCODE("Item:"),
    /** Label text is "Description:" — despite the constant name, this reads the Batch Item Name display value. */
    BATCH_ITEM_NAME("Description:"),
    QUANTITY("Quantity:");

    private final String labelText;

    ItemInformationDetailsFormLabel(String labelText) {
        this.labelText = labelText;
    }

    @Override
    public String getLabelText() {
        return labelText;
    }

    @Override
    public XpathUtils.FormSection getSection() {
        return XpathUtils.FormSection.ITEM_INFORMATION;
    }
}

