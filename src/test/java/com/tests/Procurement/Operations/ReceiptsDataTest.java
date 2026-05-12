package com.tests.Procurement.Operations;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.driver.DriverManager;
import com.gsl.enums.TestAuthor;
import com.gsl.testdata.UserCredential;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Procurement.MainMenuType;
import com.gsl.pages.enums.Procurement.Operations.OperationsMenuType;
import com.gsl.pages.Procurement.Operations.GoodsReceiveChallanAdd;
import com.gsl.pages.Procurement.Operations.ReceiptsListPage;
import com.gsl.testdata.MergedReceiptData;
import com.gsl.testdata.Procurement.*;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.dataprovider.MergedReceiptDataSupplier;
import com.tests.WebTest;
import org.testng.annotations.Test;

public class ReceiptsDataTest extends WebTest {
    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To check the creation of Receipts from UI", dataProvider = "receiptData", dataProviderClass = MergedReceiptDataSupplier.class)
    public void receiptsDataTest(MergedReceiptData data) {
        // 1. Login to the application
        UserCredential u = DriverManager.getCurrentUser();
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(u.username(), u.password());

        // 2. Use the navigation utility to get to the correct screen
        ReceiptsListPage receiptsListPage = NavigationUtils.navigateToProcurementSideMenu(homePage,
                MainMenuType.OPERATIONS,
                OperationsMenuType.RECEIPTS, ReceiptsListPage.class);

        // 3.Click on Add from the Receipts List Page to open Goods Receive Add Challan page
        //After that provide Receipt No.
        //After that provide Vendor name
        GoodsReceiveChallanAdd goodsReceiveChallanAdd = receiptsListPage
                .clickAdd()
                .setDocScheme(data.getReceiptHeader().getReceiptNo())
                .setVendor(data.getReceiptHeader().getVendorName())
                .getVendorType();

        String vendorType = goodsReceiveChallanAdd.fetchVendorType();

        System.out.println("Test Login UserName:" + DriverManager.getCurrentUser().username());
        System.out.println("Test Case: " + data.getMasterTestData().getTestcasename());
        System.out.println("Vendor: " + data.getReceiptHeader().getVendorName());

        data.getScanItems().forEach(scanItemWithDetails -> {
            System.out.println("  Item ID: " + scanItemWithDetails.getScanItem().getItemId());
            scanItemWithDetails.getItemDetails().forEach(itemDetail -> {
                System.out.println("    Item Type: " + itemDetail.getItemType());

                if (itemDetail instanceof ItemItemDetail) {
                    ItemItemDetail item = (ItemItemDetail) itemDetail;
                    System.out.println("      Item Selection Major Method: " + item.getItemSelectionMajorMethod());
                    System.out.println("      Item/Set Selection ways: " + item.getItemSetSelectionSelectionWays());
                } else if (itemDetail instanceof ScanItemAdapter) {
                    ScanItemAdapter scanItem = (ScanItemAdapter) itemDetail;
                    System.out.println("      Item Selection Major Method: " + scanItem.getItemSelectionMajorMethod());
                    System.out.println("      Item/Set Selection ways: " + scanItem.getItemSetSelectionSelectionWays());
                }


                if (itemDetail instanceof BatchItemDetail) {
                    BatchItemDetail batch = (BatchItemDetail) itemDetail;
                    System.out.println("      Batch Code: " + batch.getBatchCode());
                    System.out.println("      Expiry Date: " + batch.getExpiryDate());
                } else if (itemDetail instanceof SerialItemDetail) {
                    SerialItemDetail serial = (SerialItemDetail) itemDetail;
                    System.out.println("      Serial Numbers: " + serial.getSerialNumbers());
                } else if (itemDetail instanceof RegularItemDetail) {
                    RegularItemDetail regular = (RegularItemDetail) itemDetail;
                    System.out.println("      Item Code: " + regular.getItemCode());
                    System.out.println("      Quantity: " + regular.getQuantity());
                }
            });
        });
    }
}
