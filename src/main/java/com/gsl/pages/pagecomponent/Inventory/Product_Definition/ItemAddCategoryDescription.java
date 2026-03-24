package com.gsl.pages.pagecomponent.Inventory.Product_Definition;

import com.gsl.enums.WaitType;
import com.gsl.pages.enums.Inventory.Product_Definition.CategoryLevel;
import com.gsl.pages.enums.Inventory.Product_Definition.DescriptionLevel;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;

public class ItemAddCategoryDescription {

    private static final String CATEGORY_XPATH_TEMPLATE = "//*[@id='cbo_Category%s']";
    private static final String CATEGORY_LIST_XPATH_TEMPLATE = "//span[translate(normalize-space(text()),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')=translate('%s','ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')]";
    private static final String DESCRIPTION_XPATH_TEMPLATE = "//*[@id='cbo_Description%s']";
    private static final String DESCRIPTION_LIST_XPATH_TEMPLATE = "//span[translate(normalize-space(text()),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')=translate('%s','ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')]";
    public static String getCategoryXpathTemplate() {return CATEGORY_XPATH_TEMPLATE;}
    public static String getCategoryListXpathTemplate() {return CATEGORY_LIST_XPATH_TEMPLATE;}
    public static String getDescriptionXpathTemplate() {return DESCRIPTION_XPATH_TEMPLATE;}
    public static String getDescriptionListXpathTemplate() {return DESCRIPTION_LIST_XPATH_TEMPLATE;}



    public ItemAddCategoryDescription setCategory1(String category) {
        System.out.println("Inside category 1"+category);
        return setCategory(CategoryLevel.CAT1, category);}
    public ItemAddCategoryDescription setCategory2(String category) {return setCategory(CategoryLevel.CAT2, category);}
    public ItemAddCategoryDescription setCategory3(String category) {return setCategory(CategoryLevel.CAT3, category);}
    public ItemAddCategoryDescription setCategory4(String category) {return setCategory(CategoryLevel.CAT4, category);}
    public ItemAddCategoryDescription setCategory5(String category) {return setCategory(CategoryLevel.CAT5, category);}
    public ItemAddCategoryDescription setCategory6(String category) {return setCategory(CategoryLevel.CAT6, category);}

    public ItemAddCategoryDescription setDescription1(String description) {return setDescription(DescriptionLevel.DESC1,description);}
    public ItemAddCategoryDescription setDescription2(String description) {return setDescription(DescriptionLevel.DESC2,description);}
    public ItemAddCategoryDescription setDescription3(String description) {return setDescription(DescriptionLevel.DESC3,description);}
    public ItemAddCategoryDescription setDescription4(String description) {return setDescription(DescriptionLevel.DESC4,description);}
    public ItemAddCategoryDescription setDescription5(String description) {return setDescription(DescriptionLevel.DESC5,description);}
    public ItemAddCategoryDescription setDescription6(String description) {return setDescription(DescriptionLevel.DESC6,description);}

    private ItemAddCategoryDescription setCategory(CategoryLevel level, String value) {

        System.out.println(value);
        System.out.println("Before sendkeys");

        SeleniumUtils.sendKeys(
                level.field(),
                WaitType.CLICKABLE,
                value,
                level.fieldLabel()
        );

        System.out.println("After sendkeys");

        JavascriptExecutorUtils.waitForExtJS();

        System.out.println("Before span click");
        SeleniumUtils.click(
                level.selectionFor(value),
                WaitType.CLICKABLE,
                level.selectionLabel()
        );

        System.out.println("after span click");

        return this;
    }

    private ItemAddCategoryDescription setDescription(DescriptionLevel level, String value) {

        System.out.println(value);

        SeleniumUtils.sendKeys(
                level.field(),
                WaitType.CLICKABLE,
                value,
                level.fieldLabel()
        );

        JavascriptExecutorUtils.waitForExtJS();

        SeleniumUtils.click(
                level.selectionFor(value),
                WaitType.CLICKABLE,
                level.selectionLabel()
        );

        return this;
    }

}
