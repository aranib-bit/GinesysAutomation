package com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan;

import com.gsl.constants.FrameworkConstants;
import com.gsl.enums.WaitType;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;

public class GoodsReceiveChallanFooter {

    private static final By RECEIPTS_SAVE=By.xpath("//span[text()='ave']");

    public DocumentMessageBox clickSave(){
        SeleniumUtils.click(RECEIPTS_SAVE, WaitType.CLICKABLE, "RECEIPTS_SAVE");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "GINESYS LOADER");
        return new DocumentMessageBox();
    }
}
