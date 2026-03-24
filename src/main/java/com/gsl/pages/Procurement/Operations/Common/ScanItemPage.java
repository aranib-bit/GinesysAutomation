package com.gsl.pages.Procurement.Operations.Common;

import com.gsl.enums.WaitType;
import com.gsl.pages.Procurement.Operations.GoodsReceiveChallanAdd;
import com.gsl.reports.ExtentLogger;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.WaitUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;

public class ScanItemPage {
    private static final By ITEMSCANINPUT=By.xpath("//input[contains(@name, 'textfield-') and contains(@class, 'x-form-field')]");
    private static final By SCANITEMCANCEL=By.xpath("//span[text()='ancel']");



    public ScanItemPage addScanItem(String Mode, String ScanMode, String ItemID, String Quantity,String BatchNo, String MfgDate,String Serialno){
        ExtentLogger.info(String.format(
                "Mode=%s, ScanMode=%s, ItemID=%s, Qty=%s, BatchNo=%s, MfgDate=%s, SerialNo=%s",
                Mode, ScanMode, ItemID, Quantity, BatchNo, MfgDate, Serialno
        ));

        System.out.printf("Mode=%s, ScanMode=%s, ItemID=%s, Qty=%s, BatchNo=%s, MfgDate=%s, SerialNo=%s%n",
                Mode, ScanMode, ItemID, Quantity, BatchNo, MfgDate, Serialno);


        WaitUtils.fluentWait(ITEMSCANINPUT);
        SeleniumActions.moveToElementAndSendKeys(ITEMSCANINPUT, ItemID);
        SeleniumActions.sendKeyToElement(ITEMSCANINPUT, Keys.ENTER);
        JavascriptExecutorUtils.waitForExtJS();
        if (BatchNo != null && !BatchNo.isEmpty()) {
            // Handle batch popup
            BatchDetailsPopup batchPopup = new BatchDetailsPopup();
            batchPopup.setBatchNo(BatchNo)
                    .setMfgDate(MfgDate)
                    .clickAccept();
        }

        if (Serialno != null && !Serialno.isEmpty()) {
            // Handle serial popup
            SerialDetailsPopup serialPopup = new SerialDetailsPopup();
            serialPopup.setMfgDate(MfgDate)
                    .setSerialNo(Serialno)
                    .clickAccept();
        }

        // Wait for ExtJS to process the first item and for the UI to be ready
        //System.out.println("Waiting for ExtJS to process first item...");
        JavascriptExecutorUtils.waitForExtJS();
        return this;
    }

    public GoodsReceiveChallanAdd ScanItemCancel(){
        SeleniumUtils.click(SCANITEMCANCEL, WaitType.CLICKABLE, "SCANITEMCANCEL");
        return new GoodsReceiveChallanAdd();
    }
}
