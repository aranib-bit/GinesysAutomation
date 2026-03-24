package com.gsl.pages.pagecomponent.Inventory.Product_Definition;

import com.gsl.enums.WaitType;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class ItemAddPricing {

    private static final By STANDARDRATE=By.xpath("//*[@id='num_StdRate']");
    private static final By WSP=By.xpath("//*[@id='num_WSP']");
    private static final By RSP= By.xpath("//*[@id='num_Rsp']");
    private static final By MRP=By.xpath("//*[@id='num_Mrp']");

    public ItemAddPricing setstandardrate(String standardrate){
        standardrate= String.valueOf(FakerUtils.randomInt(10,10000));
        SeleniumUtils.sendKeys(STANDARDRATE,WaitType.CLICKABLE,standardrate,"STANDARDRATE");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public ItemAddPricing setWSP(String wsp){
        wsp= String.valueOf(FakerUtils.randomInt(10,10000));
        SeleniumUtils.sendKeys(WSP,WaitType.CLICKABLE,wsp,"WSP");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public ItemAddPricing setRSP(String rsp){
        rsp= String.valueOf(FakerUtils.randomInt(10,10000));
        SeleniumUtils.sendKeys(RSP,WaitType.CLICKABLE,rsp,"RSP");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public ItemAddPricing setMRP(String mrp){
        mrp= String.valueOf(FakerUtils.randomInt(10,10000));
        SeleniumUtils.sendKeys(MRP,WaitType.CLICKABLE,mrp,"MRP");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }


}
