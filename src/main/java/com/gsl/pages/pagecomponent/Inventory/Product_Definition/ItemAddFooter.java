package com.gsl.pages.pagecomponent.Inventory.Product_Definition;

import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;

public class ItemAddFooter {
    private static final By CREATEITEM= By.xpath("//span[text()='Create Item']");
    private static final By TOAST_MESSAGE_HEADER=By.xpath("//div[contains(@id,'toast-') and contains(@id,'-innerCt')]");
    private static final By TOAST_MESSAGE=By.xpath("//div[contains(@id,'toast-') and contains(@id,'-innerCt') and contains(@class,'x-autocontainer-innerCt')]");
    private static final By CLOSE_BUTTON=By.xpath("//span[normalize-space(.)='Close']");

    public ItemAddFooter clickCreateItem(){
        SeleniumUtils.click(CREATEITEM, WaitType.CLICKABLE,"CREATEITEM");
        WaitUtils.fluentWait(TOAST_MESSAGE);
        String createitemdetails=SeleniumUtils.getText(TOAST_MESSAGE,"TOAST_MESSAGE");
        String createitemdetails1=SeleniumUtils.getText(TOAST_MESSAGE_HEADER,"TOAST_MESSAGE");
        System.out.println(createitemdetails);
        System.out.println(createitemdetails1);
        return this;
    }

    public DocumentMessageBox clickClose(){
        SeleniumUtils.click(CLOSE_BUTTON,WaitType.CLICKABLE,"CLOSE_BUTTON");
        return new DocumentMessageBox();
    }
}
