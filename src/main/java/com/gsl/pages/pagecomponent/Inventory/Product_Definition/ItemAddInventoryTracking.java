package com.gsl.pages.pagecomponent.Inventory.Product_Definition;

import com.gsl.enums.WaitType;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Selenium.ExtJsUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ItemAddInventoryTracking {

    private static final By INVENTORYSELECTOR=By.xpath("//label[.//span[normalize-space()='Inventory Management:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final String INVENTORYMANAGEMENTSELECTIONOPTION="//li[@role='option' and normalize-space()='%s']";
    private static final By PRICINGSELECTOR=By.xpath("//label[.//span[normalize-space()='Pricing Management:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final String PRICINGMANAGEMENTSELECTIONOPTION="//li[@role='option' and normalize-space()='%s']";

    private static final By EXPIRYSELECTOR=By.xpath("//label[.//span[normalize-space()='Expiry Management :']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final String EXPIRYMANAGEMENTSELECTIONOPTION="//li[@role='option' and normalize-space()='%s']";

    private static final By VALIDITYMODESELECTOR=By.xpath("//label[.//span[normalize-space()='Validity Mode:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final String VALIDITYMODESELECTIONOPTION="//li[@role='option' and normalize-space()='%s']";

    private static final By VALIDITYPERIOD=By.xpath("//span[normalize-space()='Validity Period:'] /ancestor::label /following-sibling::div[contains(@class,'x-form-item-body')]  //input[@data-ref='inputEl' and @aria-hidden='false']");

    //private static final By INVENTORYMANAGEMENTSELECTIONOPTION=By.xpath("//li[@role='option' and normalize-space()='Batch Wise']");

    public ItemAddInventoryTracking setinventorymanagement(String inventorymanagement){
    String status=SeleniumUtils.getAttribute(PRICINGSELECTOR,"aria-disabled","PRICINGSELECTOR");
    System.out.println("status:"+status);
    SeleniumUtils.click(INVENTORYSELECTOR,WaitType.CLICKABLE,"INVENTORYSELECTOR");
    JavascriptExecutorUtils.waitForExtJS();
    SeleniumUtils.click(getInventorymanagementSelection(inventorymanagement),WaitType.CLICKABLE,"INVENTORYMANAGEMENTSELECTIONOPTION");
    JavascriptExecutorUtils.waitForExtJS();
    return this;
    }

    public ItemAddInventoryTracking setpricingmanagement(String pricingmanagement){
        String status=SeleniumUtils.getAttribute(PRICINGSELECTOR,"aria-disabled","PRICINGSELECTOR");
        System.out.println("status:"+status);
        SeleniumUtils.click(PRICINGSELECTOR,WaitType.CLICKABLE,"PRICINGSELECTOR");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getPricingmanagementSelection(pricingmanagement),WaitType.CLICKABLE,"PRICINGMANAGEMENTSELECTIONOPTION");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }


    public ItemAddInventoryTracking setexpirymanagement(String expirymanagement){
        ExtJsUtils.setExtJsComboByLabel("Expiry Management ",expirymanagement);




//        String status=SeleniumUtils.getAttribute(EXPIRYSELECTOR,"aria-disabled","EXPIRYSELECTOR");
//        System.out.println("status:"+status);
//        SeleniumUtils.click(EXPIRYSELECTOR,WaitType.CLICKABLE,"EXPIRYSELECTOR");
//        JavascriptExecutorUtils.waitForExtJS();
//        SeleniumUtils.click(getExpiringmanagementSelection(expirymanagement),WaitType.CLICKABLE,"EXPIRYMANAGEMENTSELECTIONOPTION");
//        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public ItemAddInventoryTracking setvaliditymode(String validitymode){
        String status=SeleniumUtils.getAttribute(VALIDITYMODESELECTOR,"aria-disabled","VALIDITYMODESELECTOR");
        System.out.println("status:"+status);
        SeleniumUtils.click(VALIDITYMODESELECTOR,WaitType.CLICKABLE,"VALIDITYMODESELECTOR");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getValiditymodeSelector(validitymode),WaitType.CLICKABLE,"VALIDITYMODESELECTIONOPTION");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public ItemAddInventoryTracking setvalidityperiod(String validityperiod){
        JavascriptExecutorUtils.waitForExtJS();
        String status=SeleniumUtils.getAttribute(VALIDITYPERIOD,"aria-disabled","VALIDITYPERIOD");
        System.out.println("validity period status:"+status);

        SeleniumUtils.sendKeys(VALIDITYPERIOD,WaitType.CLICKABLE,validityperiod,"VALIDITYPERIOD");

        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }




    private By getInventorymanagementSelection(String inventorymanagement) {
        String xpath = String.format(INVENTORYMANAGEMENTSELECTIONOPTION, inventorymanagement);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    private By getPricingmanagementSelection(String pricingmanagement) {
        String xpath = String.format(PRICINGMANAGEMENTSELECTIONOPTION, pricingmanagement);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    private By getExpiringmanagementSelection(String expirymanagement) {
        String xpath = String.format(EXPIRYMANAGEMENTSELECTIONOPTION, expirymanagement);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    private By getValiditymodeSelector(String validitymode) {
        String xpath = String.format(VALIDITYMODESELECTIONOPTION, validitymode);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }




}
