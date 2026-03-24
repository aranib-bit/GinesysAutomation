package com.gsl.pages.pagecomponent.Inventory.Product_Definition;

import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ItemAddGeneral {
    private static final By GENERATEGS1CODEBUTTON=By.xpath("//span[text()='enerate GS1 Code']");
    private static final By ARTICLE= By.xpath("//*[@id='cbo_ArticleName']");
    private static final String ARTICLESELECTION="//span[text()='%s']";
    private static final By HSNSACID=By.xpath("//*[@id='cbo_HSNCode']");
    private static final String HSNSACIDSELECTION="//span[text()='%s']";
    private static final By ITCELIGIBILITYSELECTOR=By.xpath("//label[.//span[normalize-space()='ITC Eligibility:']] /following-sibling::div[@data-ref='bodyEl']  //div[contains(@class,'x-form-arrow-trigger')]");
    private static final String ITCELIGIBILITYSELECTOROPTION="//li[@role='option' and normalize-space()='%s']";

    public ItemAddGeneral clickGenerateGS1Code(){
        SeleniumUtils.click(GENERATEGS1CODEBUTTON,WaitType.CLICKABLE,"GENERATEGS1CODEBUTTON");
        return this;
    }

    public ItemAddGeneral setArticle(String Article){
        SeleniumUtils.sendKeys(ARTICLE, WaitType.CLICKABLE,Article,"ARTICLE");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(getArticleSelection(Article),WaitType.CLICKABLE,"ARTICLESELECTION");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        return this;
    }

    private By getArticleSelection(String articlename) {
        return By.xpath(String.format(ARTICLESELECTION, articlename));
    }

    public ItemAddGeneral setHSNSACID(Integer hsnsacid){
        SeleniumUtils.clear(HSNSACID,"HSNSACID");
        SeleniumUtils.sendKeys(HSNSACID, WaitType.CLICKABLE, String.valueOf(hsnsacid),"HSNSACID");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        SeleniumUtils.click(getHsnsacSelection(String.valueOf(hsnsacid)),WaitType.CLICKABLE,"HSNSACIDSELECTION");
        JavascriptExecutorUtils.waitForExtJS();
        JavascriptExecutorUtils.waitForPageLoad();
        return this;
    }

    private By getHsnsacSelection(String hsnsacid) {
        return By.xpath(String.format(HSNSACIDSELECTION, hsnsacid));
    }

    public ItemAddGeneral setitceligibity(String itceligibity){
        SeleniumUtils.click(ITCELIGIBILITYSELECTOR,WaitType.CLICKABLE,"ITCELIGIBILITYSELECTOR");
        JavascriptExecutorUtils.waitForExtJS();
        SeleniumUtils.click(getItceligibilityselectoion(itceligibity),WaitType.CLICKABLE,"ITCELIGIBILITYSELECTOROPTION");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    private By getItceligibilityselectoion(String itceligibity) {
        String xpath = String.format(ITCELIGIBILITYSELECTOROPTION, itceligibity);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }
}
