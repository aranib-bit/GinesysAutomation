package com.gsl.pages.pagecomponent.Procurement.Operations.GoodsReceiveChallan;

import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.pages.Procurement.Operations.Common.SelectOrderItemPage;
import com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType.ItemSelectionType;
import com.gsl.pages.Procurement.Operations.Common.CopyChallanPage;
import com.gsl.pages.Procurement.Operations.Common.ScanItemPage;
import com.gsl.pages.Procurement.Operations.Common.SelectItemPage;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class GoodsReceiveChallanItemDetails {
    private static final By ITEMDETAILSSECTION=By.xpath("//div[text()='Item Details']");
    private static final By ITEMPOPULATIONLISTSELECTOR=By.xpath("//*[@class='mdi mdi-20px mdi-dots-horizontal']");
    private static final String ITEMSELECTOR = "//span[text()='%s' and contains(@class, 'toolbar-small')]";
    private static final By CALCULATECHARGE=By.xpath("//span[text()='Calcula']");

    public void scrollToItemDetails() {
        SeleniumActions.scrollToElement(ITEMDETAILSSECTION);
    }

    public void  clickItemPopulationSelector(){
        SeleniumUtils.click(ITEMPOPULATIONLISTSELECTOR, WaitType.CLICKABLE, "ITEMPOPULATIONLISTSELECTOR");
    }

    public Object clickItemSelector(ItemSelectionType itemSelectionType) {
        JavascriptExecutorUtils.waitForExtJS();

        String xpath = String.format(ITEMSELECTOR, itemSelectionType.getLabel());
        By locator = By.xpath(xpath);

        WebDriverWait wait = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(10));
        wait.until(ExpectedConditions.visibilityOfElementLocated(locator));

        SeleniumActions.moveToElementAndClick(locator);
        JavascriptExecutorUtils.waitForExtJS();

        if (itemSelectionType == ItemSelectionType.SCAN_ITEM) {
            return new ScanItemPage();
        } else if (itemSelectionType == ItemSelectionType.SELECT_ITEM) {
            return new SelectItemPage();
        } else if (itemSelectionType == ItemSelectionType.SELECT_ORDER_ITEMS) {
            return new SelectOrderItemPage();
        } else if (itemSelectionType == ItemSelectionType.COPY_CHALLAN) {
            return new CopyChallanPage();
        }
        return null;
    }
    public void clickCalculateCharge(){
        SeleniumUtils.click(CALCULATECHARGE, WaitType.CLICKABLE, "CALCULATECHARGE");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "GINESYSLOADER");
    }
}

