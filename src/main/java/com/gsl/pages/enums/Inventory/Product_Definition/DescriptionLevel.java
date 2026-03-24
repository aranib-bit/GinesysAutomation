package com.gsl.pages.enums.Inventory.Product_Definition;

import org.openqa.selenium.By;

import static com.gsl.pages.pagecomponent.Inventory.Product_Definition.ItemAddCategoryDescription.*;

public enum DescriptionLevel {
    DESC1("1"),
    DESC2("2"),
    DESC3("3"),
    DESC4("4"),
    DESC5("5"),
    DESC6("6");

    private final String index;

    DescriptionLevel(String index) {
        this.index = index;
    }

    public By field() {
        return By.xpath(
                String.format(getDescriptionXpathTemplate(), index)
        );
    }

    public By selectionFor(String value) {
        return By.xpath(
                String.format(getDescriptionListXpathTemplate(), value, index)
        );
    }

    public String fieldLabel() {
        return "DESCRIPTION" + index;
    }

    public String selectionLabel() {
        return "DESCRIPTION" + index + "_SELECTION";
    }
}
