package com.gsl.pages.enums.Procurement.Common;

import com.gsl.utils.Selenium.XpathUtils;

public enum ItemInformationDetailsFormLabel implements XpathUtils.LabelEnum {
    ITEM_ICODE_BARCODE("Item:"),
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

