package com.gsl.pages.enums.Inventory.Product_Definition;

import org.openqa.selenium.By;

import static com.gsl.pages.pagecomponent.Inventory.Product_Definition.ItemAddCategoryDescription.*;

/**
 * The six Category combo-box levels (CAT1..CAT6) on the Item Add/Edit page's Category &amp;
 * Description panel — consumed by {@code ItemAddCategoryDescription.setCategory1()..setCategory6()},
 * each of which calls this level's {@link #field()} to type into the combo-box then {@link
 * #selectionFor(String)} to click the matching dropdown option. The numeric {@code index} is
 * substituted into {@code ItemAddCategoryDescription}'s shared xpath templates (id {@code
 * cbo_Category<N>} for the field, a case-insensitive text match for the dropdown option).
 */
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

    /** @return locator for this level's Category combo-box input field (id {@code cbo_Category<N>}). */
    public By field() {
        return By.xpath(
                String.format(getCategoryXpathTemplate(), index)
        );
    }

    /** @return locator for the {@code value} option in this level's dropdown list (case-insensitive match). */
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


    /** @return report label for this level's field action, e.g. "CATEGORY1". */
    public String fieldLabel() {
        return "CATEGORY" + index;
    }

    /** @return report label for this level's dropdown-selection action, e.g. "CATEGORY1_SELECTION". */
    public String selectionLabel() {
        return "CATEGORY" + index + "_SELECTION";
    }
}

