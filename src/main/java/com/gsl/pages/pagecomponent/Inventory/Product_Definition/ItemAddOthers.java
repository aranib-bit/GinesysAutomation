package com.gsl.pages.pagecomponent.Inventory.Product_Definition;

import com.aventstack.extentreports.gherkin.model.ScenarioOutline;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.*;
import org.apache.commons.lang3.SystemUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

import static com.gsl.driver.DriverManager.getDriver;

public class ItemAddOthers {

    private static final By ITEMNAME= By.xpath("//*[@id='txt_ItemName']");
    private static  final By SHORTITEMNAME=By.xpath("//*[@id='cbo_ShortName']");
    private static final String SHORTITEMNAMESELECTION= "//span[text()='%s']";
    private static final By VENDORNAME= By.xpath("//*[@id='cbo_VendorName']");
    private static final String VENDORNAMESELECTION= "//span[text()='%s']";
    private static final By TAXGROUPNAME= By.xpath("//*[@id='cbo_TaxGrpName']");
    private static final String TAXGROUPNAMESELECTION= "//span[text()='%s']";
    private static final By MATERIALTYPESELECTOR=By.xpath("//label[.//span[normalize-space()='Material Type:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger')]");
    private static final String MATERIALTYPESELECTOROPTION="//li[@role='option' and normalize-space()='%s']";
    private static final By COSTSHEETNAME= By.xpath("//*[@id='cbo_CostSheet']");
    private static final String COSTSHEETNAMESELECTION= "//span[text()='%s']";
    private static final By GENERALLEDGERNAME=By.xpath("//span[normalize-space()='General Ledger:'] /ancestor::label /following-sibling::div[contains(@class,'x-form-item-body')]  //input[@data-ref='inputEl']");
    private static final String GENERALLEDGERNAMESELECTION= "//span[text()='%s']";
    private static final By SUBLEDGERNAME=By.xpath("//span[normalize-space()='Sub Ledger:'] /ancestor::label /following-sibling::div[contains(@class,'x-form-item-body')]  //input[@data-ref='inputEl' and @aria-hidden='false']");
    private static final String SUBLEDGERNAMESELECTION="//span[text()='%s']";
    private static final By UNITOFMEASUREMENT= By.xpath("//*[@id='cbo_Uom']");
    private static final String UNITOFMEASUREMENTSELECTION= "//span[text()='%s']";
    private static final By NEGATIVESTOCKMETHODSELECTOR=By.xpath("//label[.//span[normalize-space()='Negative Stock Method:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger')]");
    private static final String NEGATIVESTOCKMETHODSELECTOROPTION="//li[@role='option' and normalize-space()='%s']";
    private static final By INVENTORYITEMSELECTOR=By.xpath("//label[.//span[normalize-space()='Inventory Item:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger')]");
    private static final String INVENTORYITEMSELECTOROPTION="//li[@role='option' and normalize-space()='%s']";
    private static final By SCANUNIT=By.xpath("//*[@id='num_ScanUnit']");
    private static final By NUMBER1=By.xpath("//*[@id='num_Number1']");
    private static final By NUMBER2=By.xpath("//*[@id='num_Number2']");
    private static final By NUMBER3=By.xpath("//*[@id='num_Number3']");
    private static final By REMARKS=By.xpath("//*[@id='txt_Remarks']");

    public ItemAddOthers setItemName(String itemName){
        SeleniumUtils.sendKeys(ITEMNAME,WaitType.CLICKABLE,itemName,"ITEMNAME");
        return this;
    }

    public ItemAddOthers setshortitemname(String shortitemname){
        SeleniumUtils.sendKeys(SHORTITEMNAME, WaitType.CLICKABLE,shortitemname,"SHORTITEMNAME");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(getshortnameSelection(shortitemname), WaitType.CLICKABLE, "SHORTITEMNAMESELECTION");
        return this;
    }

    private By getshortnameSelection(String shortitemname) {
        return By.xpath(String.format(SHORTITEMNAMESELECTION, shortitemname));
    }

    public ItemAddOthers setvendorname(String vendorname){
        SeleniumUtils.sendKeys(VENDORNAME, WaitType.CLICKABLE,vendorname,"VENDORNAME");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(getvendornameSelection(vendorname), WaitType.CLICKABLE, "VENDORNAMESELECTION");
        return this;
    }

    private By getvendornameSelection(String vendorname) {
        return By.xpath(String.format(VENDORNAMESELECTION, vendorname));
    }

    public ItemAddOthers settaxgroup(String taxgrpname){
        SeleniumUtils.clear(TAXGROUPNAME,"TAXGROUPNAME");
        SeleniumUtils.sendKeys(TAXGROUPNAME, WaitType.CLICKABLE,taxgrpname,"TAXGROUPNAME");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(gettaxgrpnameSelection(taxgrpname), WaitType.CLICKABLE, "TAXGROUPNAMESELECTION");
        return this;
    }

    private By gettaxgrpnameSelection(String taxgrpname) {
        return By.xpath(String.format(TAXGROUPNAMESELECTION, taxgrpname));
    }

    public ItemAddOthers setcostsheetname(String costsheetname){
        SeleniumUtils.sendKeys(COSTSHEETNAME, WaitType.CLICKABLE,costsheetname,"COSTSHEETNAME");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(getcostsheetnameSelection(costsheetname), WaitType.CLICKABLE, "COSTSHEETNAMESELECTION");
        return this;
    }

    private By getcostsheetnameSelection(String costsheetname) {
        return By.xpath(String.format(COSTSHEETNAMESELECTION, costsheetname));
    }


    public ItemAddOthers setgeneralledgername(String generalledgername){
        SeleniumUtils.sendKeys(GENERALLEDGERNAME, WaitType.CLICKABLE,generalledgername,"GENERALLEDGERNAME");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(getgeneralledgernameSelection(generalledgername), WaitType.CLICKABLE, "GENERALLEDGERNAMESELECTION");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        return this;
    }

    private By getgeneralledgernameSelection(String generalledgername) {
        return By.xpath(String.format(GENERALLEDGERNAMESELECTION, generalledgername));
    }

    public ItemAddOthers setsubledgername(String subLedgerName) {

        System.out.println("Setting SubLedger: " + subLedgerName);

        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(SUBLEDGERNAME, WaitType.CLICKABLE, "SUBLEDGERNAME");
        SeleniumUtils.clear(SUBLEDGERNAME, "SUBLEDGERNAME");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.sendKeys(SUBLEDGERNAME, WaitType.CLICKABLE, subLedgerName,"SUBLEDGERNAME");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        //if the send keys fails in first time then 2nd time it is set using ExtJsUtils
        try {
            SeleniumUtils.click(getsubledgernameSelection(subLedgerName), WaitType.CLICKABLE, "SUBLEDGERNAMESELECTION");
        } catch (Exception e) {
            System.out.println("Inside catch block");
            ExtJsUtils.setExtJsComboByLabel("Sub Ledger",subLedgerName);
        }
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        return this;
    }

    private By getsubledgernameSelection(String subledgername) {
        return By.xpath(String.format(SUBLEDGERNAMESELECTION, subledgername));
    }

    public ItemAddOthers setUOM(String uom){
        SeleniumUtils.sendKeys(UNITOFMEASUREMENT, WaitType.CLICKABLE,uom,"UNITOFMEASUREMENT");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(getUOMSelection(uom), WaitType.CLICKABLE, "UNITOFMEASUREMENTSELECTION");
        return this;
    }

    private By getUOMSelection(String uom) {
        return By.xpath(String.format(UNITOFMEASUREMENTSELECTION, uom));
    }

    public ItemAddOthers setscanunit(String scanunit){
        SeleniumUtils.sendKeys(SCANUNIT,WaitType.CLICKABLE,scanunit,"SCANUNIT");
        return this;
    }

    public ItemAddOthers setnumber1(String number1){
        SeleniumUtils.sendKeys(NUMBER1,WaitType.CLICKABLE,number1,"NUMBER1");
        return this;
    }

    public ItemAddOthers setnumber2(String number2){
        SeleniumUtils.sendKeys(NUMBER2,WaitType.CLICKABLE,number2,"NUMBER2");
        return this;
    }

    public ItemAddOthers setnumber3(String number3){
        SeleniumUtils.sendKeys(NUMBER3,WaitType.CLICKABLE,number3,"NUMBER3");
        return this;
    }

    public ItemAddOthers setremarks(String remarks){
        SeleniumUtils.sendKeys(REMARKS,WaitType.CLICKABLE,remarks,"REMARKS");
        return this;
    }

    public ItemAddOthers setmaterialtype(String materialtype){
        SeleniumUtils.click(MATERIALTYPESELECTOR,WaitType.CLICKABLE,"MATERIALTYPESELECTOR");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getmaterialtypeselection(materialtype),WaitType.CLICKABLE,"MATERIALTYPESELECTOROPTION");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    private By getmaterialtypeselection(String materialtype) {
        String xpath = String.format(MATERIALTYPESELECTOROPTION, materialtype);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    public ItemAddOthers setinventoryitem(String inventoryitem){
        SeleniumUtils.click(INVENTORYITEMSELECTOR,WaitType.CLICKABLE,"INVENTORYITEMSELECTOR");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getinventoryitemselection(inventoryitem),WaitType.CLICKABLE,"INVENTORYITEMSELECTOROPTION");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    private By getinventoryitemselection(String inventoryitem) {
        String xpath = String.format(INVENTORYITEMSELECTOROPTION, inventoryitem);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

    public ItemAddOthers setnegativestockmethod(String negativestockmethod){
        SeleniumUtils.click(NEGATIVESTOCKMETHODSELECTOR,WaitType.CLICKABLE,"NEGATIVESTOCKMETHODSELECTOR");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getnegativestockmethodselection(negativestockmethod),WaitType.CLICKABLE,"NEGATIVESTOCKMETHODSELECTOROPTION");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    private By getnegativestockmethodselection(String negativestockmethod) {
        String xpath = String.format(NEGATIVESTOCKMETHODSELECTOROPTION, negativestockmethod);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }
}
