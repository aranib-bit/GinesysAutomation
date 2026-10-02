package com.gsl.pages.enums.Inventory.Product_Definition;

import org.openqa.selenium.By;

import static com.gsl.pages.pagecomponent.Inventory.Product_Definition.ItemAddCategoryDescription.*;

/**
 * The six Description combo-box levels (DESC1..DESC6) on the Item Add/Edit page's Category &amp;
 * Description panel — consumed by {@code ItemAddCategoryDescription.setDescription1()..
 * setDescription6()}, each of which calls this level's {@link #field()} to type into the
 * combo-box then {@link #selectionFor(String)} to click the matching dropdown option. Mirrors
 * {@link CategoryLevel} exactly, for the Description fields instead of Category. The numeric
 * {@code index} is substituted into {@code ItemAddCategoryDescription}'s shared xpath templates
 * (id {@code cbo_Description<N>} for the field, a case-insensitive text match for the dropdown
 * option).
 */
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

    /** @return locator for this level's Description combo-box input field (id {@code cbo_Description<N>}). */
    public By field() {
        return By.xpath(
                String.format(getDescriptionXpathTemplate(), index)
        );
    }

    /** @return locator for the {@code value} option in this level's dropdown list (case-insensitive match). */
    public By selectionFor(String value) {
        return By.xpath(
                String.format(getDescriptionListXpathTemplate(), value, index)
        );
    }

    /** @return report label for this level's field action, e.g. "DESCRIPTION1". */
    public String fieldLabel() {
        return "DESCRIPTION" + index;
    }

    /** @return report label for this level's dropdown-selection action, e.g. "DESCRIPTION1_SELECTION". */
    public String selectionLabel() {
        return "DESCRIPTION" + index + "_SELECTION";
    }
}
