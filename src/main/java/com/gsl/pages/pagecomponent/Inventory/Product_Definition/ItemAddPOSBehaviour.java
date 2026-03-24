package com.gsl.pages.pagecomponent.Inventory.Product_Definition;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.ExtJsUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.apache.commons.io.ThreadUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;

import java.util.concurrent.TimeUnit;

public class ItemAddPOSBehaviour {
    private static final By MULTIPRICEACTIONSELECTOR=By.xpath("//label[.//span[normalize-space()='Multi Price Action:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final String MULTIPRICEACTIONMANAGEMENTSELECTIONOPTION="//li[@role='option' and normalize-space()='%s']";
    private static final By ALLOWPRICECHANGESELECTOR=By.xpath("//label[.//span[normalize-space()='Allow Price Change:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final By ALLOWPRICECHANGELABEL=By.xpath("//label[.//span[normalize-space()='Allow Price Change:']]");
    private static final By AUTOQTYPOPUPLABEL=By.xpath("//label[.//span[normalize-space()='Allow Price Change:']]");
    private static final String ALLOWPRICECHANGESELECTIONOPTION="//li[@role='option' and normalize-space()='%s']";
    private static final By AUTOQTYPOPUPSELECTOR=By.xpath("//label[.//span[normalize-space()='Auto Qty. Popup:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final String AUTOQTYPOPUPSELECTIONOPTION="//li[@role='option' and normalize-space()='%s']";
    private static final By CHARGEEXTRATAXSELECTOR=By.xpath("//label[.//span[normalize-space()='Charge Extra Tax:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final By CHARGEEXTRATAXLABEL=By.xpath("//label[.//span[normalize-space()='Charge Extra Tax:']]");

    private static final String CHARGEEXTRATAXOPTION="//li[@role='option' and normalize-space()='%s']";
    private static final By RETURNBEHAVIOURSELECTOR=By.xpath("//label[.//span[normalize-space()='Return Behavior:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger') and contains(@role,'presentation')]");
    private static final String RETURNBEHAVIOUROPTION="//li[@role='option' and normalize-space()='%s']";

    private static final By PRICECHANGESLIMIT=By.xpath("//span[normalize-space()='Price Changes Limit (in %):'] /ancestor::label /following-sibling::div[contains(@class,'x-form-item-body')]  //input[@data-ref='inputEl' and @aria-hidden='false']");

    public ItemAddPOSBehaviour setmultipriceaction(String multipriceaction){
        SeleniumUtils.click(MULTIPRICEACTIONSELECTOR, WaitType.CLICKABLE,"MULTIPRICEACTIONSELECTOR");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getMultipriceactionselector(multipriceaction),WaitType.CLICKABLE,"MULTIPRICEACTIONMANAGEMENTSELECTIONOPTION");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    private By getMultipriceactionselector(String multipriceaction) {
        String xpath = String.format(MULTIPRICEACTIONMANAGEMENTSELECTIONOPTION, multipriceaction);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }


    public ItemAddPOSBehaviour setpricechangeslimit(String pricechangeslimit){
        JavascriptExecutorUtils.waitForExtJS();
        String status=SeleniumUtils.getAttribute(PRICECHANGESLIMIT,"aria-disabled","PRICECHANGESLIMIT");
        System.out.println("price changes limit status:"+status);
        SeleniumUtils.sendKeys(PRICECHANGESLIMIT,WaitType.CLICKABLE,pricechangeslimit,"PRICECHANGESLIMIT");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public ItemAddPOSBehaviour setreturnbehavior(String returnbehavior){
        SeleniumUtils.click(RETURNBEHAVIOURSELECTOR, WaitType.CLICKABLE,"RETURNBEHAVIOURSELECTOR");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getReturnbehaviourselector(returnbehavior),WaitType.CLICKABLE,"RETURNBEHAVIOUROPTION");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    private By getReturnbehaviourselector(String returnbehavior) {
        String xpath = String.format(RETURNBEHAVIOUROPTION, returnbehavior);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }



    public ItemAddPOSBehaviour setallowpricechange(String allowpricechange){
        JavascriptExecutorUtils.waitForExtJS();
        ExtJsUtils.setExtJsComboByLabel("Allow Price Change", allowpricechange);
        //SeleniumUtils.click(ALLOWPRICECHANGELABEL, WaitType.CLICKABLE,"ALLOWPRICECHANGELABEL");
        //SeleniumUtils.click(ALLOWPRICECHANGELABEL, WaitType.CLICKABLE,"ALLOWPRICECHANGELABEL");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public ItemAddPOSBehaviour setautoqtypopup(String autoqtypopup){
        JavascriptExecutorUtils.waitForExtJS();
        ExtJsUtils.setExtJsComboByLabel("Auto Qty. Popup", autoqtypopup);
        //SeleniumUtils.click(AUTOQTYPOPUPLABEL, WaitType.CLICKABLE,"AUTOQTYPOPUPLABEL");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public ItemAddPOSBehaviour setchargeextratax(String chargeextratax){
        JavascriptExecutorUtils.waitForExtJS();
        System.out.println("Value for chargeextratax"+chargeextratax);
        ExtJsUtils.setExtJsComboByLabel("Charge Extra Tax", chargeextratax);
        //SeleniumUtils.click(CHARGEEXTRATAXLABEL, WaitType.CLICKABLE,"CHARGEEXTRATAXLABEL");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }








}
