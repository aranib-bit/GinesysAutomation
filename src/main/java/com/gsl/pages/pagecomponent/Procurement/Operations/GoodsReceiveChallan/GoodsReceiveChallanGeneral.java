package com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan;

import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class GoodsReceiveChallanGeneral {

    private static final By DOC_SCHEME_SELECTION=By.xpath("//span[text()='Receipt No.:']/ancestor::label/following-sibling::div//input[contains(@id, 'inputEl') and contains(@id,'combo')]");
    private static final String SCHEME_DOC_SELECTION="//span[text()='%s']";
    //private static final By SCHEME_DOC_SELECTION =By.xpath("//div[@class='x-boundlist-item x-boundlist-item-over']/table/tbody/tr/td");
    private static final By VENDORINPUT=By.xpath("//span[text()='Vendor Name:']/ancestor::label/following-sibling::div//input[contains(@id, 'grc_cbo_VendorName')]");
    private static final String VENDORSELECTION="//span[text()='%s']";
    //private static final By VENDORSELECTION=By.xpath("//div[@class='x-boundlist-item x-boundlist-item-over']/table/tbody/tr/td");
    private static final By VENDORTYPE=By.xpath("//*[@id='grc_cbo_GrcType']");
    private static final By OWNERSITE=By.xpath("//span[text()='Owner Site:']/ancestor::label/following-sibling::div//input[contains(@id, 'combo-') and contains(@id,'-inputEl')]");
    private static final String OWNERSITESELECTION_XPATH = "//span[text()='%s']";
    private static final By STOCKPOINT=By.xpath("//input[@id='grc_cbo_StockPoint']");
    private static final String STOCKPOINTSELECTION_XPATH="//span[text()='%s']";
    private static final String SITETYPE="//tr[td[normalize-space()='%s']]/td[@class='combooverflow'][3]";
    private static final By OWNERSITESELECTIONDROPDOWN=By.xpath("//span[text()='Owner Site:']/ancestor::label/following-sibling::div//div[contains (@id,'combo-') and contains(@id,'trigger-picker')]");
    private static final By TERMNAME=By.xpath("//*[@id='grc_cbo_TermName']");
    private static final String TERMNAMESELECTION_XPATH="//span[text()='%s']";
    private static final By TRADEGROUPNAME=By.xpath("//input[@id='grc_cbo_TradeGroupName']");
    private static final String TRADEGROUPNAMESELECTION_XPATH="//span[text()='%s']";

    private String lastSiteType; // store SiteType

    public GoodsReceiveChallanGeneral setDocScheme(String docscheme) {
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.sendKeys(DOC_SCHEME_SELECTION, WaitType.CLICKABLE, docscheme, "DOC_SCHEME_SELECTION");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getDocSchemeSelection(docscheme), WaitType.CLICKABLE, "SCHEME_DOC_SELECTION");
        //SeleniumUtils.click(SCHEME_DOC_SELECTION, WaitType.CLICKABLE, "SCHEME_DOC_SELECTION");
        return this;
    }

    public GoodsReceiveChallanGeneral setVendor(String vendorname){
        SeleniumUtils.sendKeys(VENDORINPUT, WaitType.CLICKABLE,vendorname, "VENDORINPUT");
        SeleniumUtils.click(getVendorSelection(vendorname), WaitType.CLICKABLE, "VENDORSELECTION");
        //SeleniumUtils.click(VENDORSELECTION, WaitType.CLICKABLE, "VENDORSELECTION");
        return this;
    }

    private By getOwnerSiteTypeSelection(String ownerSiteName) {
        return By.xpath(String.format(SITETYPE, ownerSiteName));
    }

    private By getOwnerSiteSelection(String ownerSiteName) {
        return By.xpath(String.format(OWNERSITESELECTION_XPATH, ownerSiteName));
    }
    private By getStockPointSelection(String stockPointName) {
        return By.xpath(String.format(STOCKPOINTSELECTION_XPATH, stockPointName));
    }

    private By getTermNameSelection(String termName) {
        String xpath = String.format(TERMNAMESELECTION_XPATH, termName);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }


    private By getTradeGroupSelection(String tradeGroupName) {
        String xpath = String.format(TRADEGROUPNAMESELECTION_XPATH, tradeGroupName);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }



    private By getDocSchemeSelection(String docscheme) {
        return By.xpath(String.format(SCHEME_DOC_SELECTION, docscheme));
    }

    private By getVendorSelection(String vendorname) {
        return By.xpath(String.format(VENDORSELECTION, vendorname));
    }

    public GoodsReceiveChallanGeneral setOwnerSite(String ownerSite) {
        String currentOwnerSite = getOwnerSite();

        if (currentOwnerSite != null && currentOwnerSite.equalsIgnoreCase(ownerSite)) {
//            SeleniumUtils.click(OWNERSITESELECTIONDROPDOWN,WaitType.CLICKABLE,"OWNERSITESELECTIONDROPDOWN");
//            SeleniumUtils.elementDisappear(By.xpath("//div[contains(@class,'x-mask-msg-text') and normalize-space()='Loading...']"), "LOADER");
//            lastSiteType = getOwnerSiteType(ownerSite);
//            System.out.println("inside site type detection 1");
//            System.out.println(lastSiteType);
//            SeleniumUtils.click(OWNERSITESELECTIONDROPDOWN,WaitType.CLICKABLE,"OWNERSITESELECTIONDROPDOWN");
            /// HARD CODED THAT DEFAULT SITE WILL BE OS-OO-CM
            //NEED TO FIX THIS ISSUE

            lastSiteType="OS-OO-CM";
            return this;
        } else {
            JavascriptExecutorUtils.waitForPageLoad();
            JavascriptExecutorUtils.waitForExtJS();
            SeleniumUtils.clear(OWNERSITE, "OWNERSITE");
            JavascriptExecutorUtils.waitForExtJS();
            SeleniumUtils.sendKeys(OWNERSITE, WaitType.CLICKABLE, ownerSite, "OWNERSITE");
            SeleniumUtils.elementDisappear(By.xpath("//div[contains(@class,'x-mask-msg-text') and normalize-space()='Loading...']"), "LOADER");

            JavascriptExecutorUtils.waitForExtJS();
            lastSiteType = getOwnerSiteType(ownerSite); // capture here
            SeleniumUtils.click(getOwnerSiteSelection(ownerSite), WaitType.CLICKABLE, "OWNERSITESELECTION");

            JavascriptExecutorUtils.waitForExtJS();
            DocumentMessageBox messageBox = new DocumentMessageBox();
            messageBox.clickProceed();

            return this;
        }
        }

    public String getLastSiteType() {
        return lastSiteType;
    }


    public String getVendorType(){
        String VendorType =SeleniumUtils.getAttribute(VENDORTYPE,"value","VENDORTYPE");
        return VendorType;
    }

    public String getOwnerSiteType(String ownerSiteName) {
        By siteTypeLocator = By.xpath(String.format(SITETYPE, ownerSiteName));
        String siteType = SeleniumUtils.getText(siteTypeLocator, "SITETYPE");
        return siteType;
    }



    public String getOwnerSite(){
        String OwnerSite =SeleniumUtils.getAttribute(OWNERSITE,"value","OWNERSITE");
        return OwnerSite;
    }

    public String getTermName(){
        String TermName=SeleniumUtils.getAttribute(TERMNAME,"value","TERMNAME");
        return TermName;
    }

    public String getTradeGroupName(){
        String TradeGroupName=SeleniumUtils.getAttribute(TERMNAME,"value","TERMNAME");
        return TradeGroupName;
    }

    public String getStockPoint(){
        String StockPoint=SeleniumUtils.getAttribute(STOCKPOINT,"value","STOCKPOINT");
        return StockPoint;
    }


    public GoodsReceiveChallanGeneral setStockPoint(String stockPoint) {

        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();

        String currentStockPoint = getStockPoint();
        System.out.println("CurrentStockPoint is "+currentStockPoint);

        if (currentStockPoint.equalsIgnoreCase(stockPoint)) {
            // Already set correctly, no action needed
            System.out.println("Inside 1st if ");
            return this;
        }

        // Else set new value
        SeleniumUtils.clear(STOCKPOINT,"STOCKPOINT");
        //JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.sendKeys(STOCKPOINT, WaitType.CLICKABLE, stockPoint, "STOCKPOINT");
        //JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.elementDisappear(By.xpath("//div[contains(@class,'x-mask-msg-text') and normalize-space()='Loading...']"),"LOADER");
        //JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getStockPointSelection(stockPoint), WaitType.CLICKABLE, "STOCKPOINTSELECTION");
//        // Handle Document Message Box
//        DocumentMessageBox messageBox = new DocumentMessageBox();
//        messageBox.clickProceed();
        return this;
    }


    public GoodsReceiveChallanGeneral setTermName(String termName) {

        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();

        String currentTermName = getTermName();
        System.out.println("CurrentTermName is "+currentTermName);

        if (currentTermName.equalsIgnoreCase(termName)) {
            // Already set correctly, no action needed
            System.out.println("Inside 1st if of term name");
            return this;
        }

        // Else set new value
        SeleniumUtils.clear(TERMNAME,"TERMNAME");
        //JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.sendKeys(TERMNAME, WaitType.CLICKABLE, termName, "TERMNAME");
        //JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.elementDisappear(By.xpath("//div[contains(@class,'x-mask-msg-text') and normalize-space()='Loading...']"),"LOADER");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getTermNameSelection(termName), WaitType.CLICKABLE, "TERMNAMESELECTION");
        return this;
    }

    public GoodsReceiveChallanGeneral setTradeGroup(String tradeGroupName) {
        JavascriptExecutorUtils.waitForPageLoad();
        JavascriptExecutorUtils.waitForExtJS();

        String currentTradeGroup = getTradeGroupName();
        System.out.println("CurrentTermName is "+currentTradeGroup);

        if (currentTradeGroup.equalsIgnoreCase(tradeGroupName)) {
            // Already set correctly, no action needed
            System.out.println("Inside 1st if of term name");
            return this;
        }

        // Else set new value
        SeleniumUtils.clear(TRADEGROUPNAME,"TRADEGROUPNAME");
        //JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.sendKeys(TRADEGROUPNAME, WaitType.CLICKABLE, tradeGroupName, "TRADEGROUPNAME");
        //JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.elementDisappear(By.xpath("//div[contains(@class,'x-mask-msg-text') and normalize-space()='Loading...']"),"LOADER");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getTradeGroupSelection(tradeGroupName), WaitType.CLICKABLE, "TRADEGROUPNAMESELECTION");
        return this;



    }
}
