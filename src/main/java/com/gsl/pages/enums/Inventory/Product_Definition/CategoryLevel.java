package com.gsl.pages.enums.Inventory.Product_Definition;

import org.openqa.selenium.By;

import static com.gsl.pages.pagecomponent.Inventory.Product_Definition.ItemAddCategoryDescription.*;

public enum CategoryLevel {

    CAT1("1"),
    CAT2("2"),
    CAT3("3"),
    CAT4("4"),
    CAT5("5"),
    CAT6("6");

    private final String index;

    CategoryLevel(String index) {
        this.index = index;
    }

    public By field() {
        return By.xpath(
                String.format(getCategoryXpathTemplate(), index)
        );
    }

    public By selectionFor(String value) {

        String normalizedValue = value.trim();

        String xpath = String.format(
                getCategoryListXpathTemplate(),
                normalizedValue,
                index
        );

        System.out.println("Selecting for [" + normalizedValue + "]");
        System.out.println("Generated xpath: " + xpath);

        return By.xpath(xpath);
    }


    public String fieldLabel() {
        return "CATEGORY" + index;
    }

    public String selectionLabel() {
        return "CATEGORY" + index + "_SELECTION";
    }
}

