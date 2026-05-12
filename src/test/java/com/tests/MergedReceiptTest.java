package com.tests;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.driver.DriverManager;
import com.gsl.testdata.UserCredential;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.Common.DocumentMessageBox;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.Procurement.Operations.GoodsReceiveChallanAdd;
import com.gsl.pages.Procurement.Operations.ReceiptsListPage;
import com.gsl.pages.Procurement.Operations.Common.ScanItemPage;
import com.gsl.pages.enums.Procurement.MainMenuType;
import com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType.ItemSelectionType;
import com.gsl.pages.enums.Procurement.Operations.GoodsReceiveChallanItemSelectionType.ItemSelectionTypeEnumMapper;
import com.gsl.pages.enums.Procurement.Operations.OperationsMenuType;
import com.gsl.testdata.dto.*;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.dataprovider.MergedReceiptDataSupplier;
import org.openqa.selenium.By;
import org.openqa.selenium.interactions.Actions;
import org.testng.Assert;
import org.testng.annotations.Test;


public class MergedReceiptTest extends WebTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To check the creation of Receipts from UI", dataProvider = "dataSupplier", dataProviderClass = MergedReceiptDataSupplier.class)
   // @Test(dataProvider = "dataSupplier", dataProviderClass = MergedReceiptDataSupplier.class)
    public void CreateReceiptUI(MergedReceiptData data) {
        System.out.println(
                "Running invocation " +
                        data.getInvocationIndex() + "/" + data.getTotalInvocations()
        );

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
                .setOwnerSite(data.getReceiptHeader().getOwnersite())
                .setDocScheme(data.getReceiptHeader().getReceiptNo())
                .setVendor(data.getReceiptHeader().getVendorName())
                .setStockPoint(data.getReceiptHeader().getStockPoint())
                .setTradeGroup(data.getReceiptHeader().getTradeGroupName())
                .setTermName(data.getReceiptHeader().getTermName())
                .getVendorType();

        String vendorType = goodsReceiveChallanAdd.fetchVendorType();
        String siteType   = goodsReceiveChallanAdd.fetchSiteType();

        for (ItemBlock block : data.getItemBlocks()) {
            ItemDetailsRow main = block.getItemDetails();

            // -------------------
            // Handle Scan Items
            // -------------------
            if (!block.getScanItems().isEmpty()) {
                // convert String → Enum
                ItemSelectionType selectionType = ItemSelectionTypeEnumMapper.mapItemSelection(main.getItemSetSelectionWay());
                // open Scan Item popup ONCE
                ScanItemPage scanItemPage = (ScanItemPage) goodsReceiveChallanAdd
                        .scrollToItemDetails()
                        .clickItemPopulationSelector()
                        .clickItemSelector(selectionType);

                // add all scan items inside
                for (ScanItemRow scan : block.getScanItems()) {
                    scanItemPage.addScanItem(scan.getMode(), scan.getScanMode(), scan.getItemId(), scan.getQuantity(), scan.getBatchno(),scan.getMfgdate(),scan.getSerialno());


//                    goodsReceiveChallanAdd.addScanItem(
//                            scan.getMode(),
//                            scan.getScanMode(),
//                            scan.getItemId(),
//                            scan.getQuantity());
                }

                // close Scan Item popup after processing
                goodsReceiveChallanAdd = scanItemPage.ScanItemCancel();
            }

            // -------------------
            // Handle Select Items
            // -------------------
//            if (!block.getSelectItems().isEmpty()) {
//                // open Select Item popup ONCE
//                goodsReceiveChallanAdd
//                        .scrollToItemDetails()
//                        .clickItemPopulationSelector()
//                        .clickItemSelector(main.getItemSetSelectionWay());
//
//                // add all select items inside
//                for (SelectItemRow sel : block.getSelectItems()) {
//                    goodsReceiveChallanAdd.addSelectItem(
//                            sel.getSearchKeyword1(),
//                            sel.getSearchValue1(),
//                            sel.getQuantity());
//                }
//
//                // close Select Item popup after processing
//                goodsReceiveChallanAdd.confirmSelectItems();
//            }
//
//            // -------------------
//            // Handle Select Sets
//            // -------------------
//            if (!block.getSelectSets().isEmpty()) {
//                goodsReceiveChallanAdd
//                        .scrollToItemDetails()
//                        .clickItemPopulationSelector()
//                        .clickItemSelector(main.getItemSetSelectionWay());
//
//                for (SelectSetRow set : block.getSelectSets()) {
//                    goodsReceiveChallanAdd.addSelectSet(
//                            set.getSetId(),
//                            set.getQuantity());
//                }
//
//                goodsReceiveChallanAdd.confirmSelectSets();
//            }
//        }


//        // -------- Item Blocks --------
//        System.out.println("---- Item Details ----");
//        for (ItemBlock block : data.getItemBlocks()) {
//            ItemDetailsRow main = block.getItemDetails();
//            //GoodsReceiveChallanAdd goodsReceiveChallanAdd1=goodsReceiveChallanAdd1.scrollToItemDetails().clickItemPopulationSelector().clickItemSelector(main.getItemSetSelectionWay());
//            System.out.println("Item Major Method : " + main.getItemSelectionMajorMethod());
//            System.out.println("Selection Way     : " + main.getItemSetSelectionWay());
//            System.out.println("Item Type         : " + main.getItemType());
//
//            // Scan Items
//            if (!block.getScanItems().isEmpty()) {
//                System.out.println("   --- Scan Items ---");
//                System.out.printf("   %-10s %-10s %-10s %-15s %-10s%n",
//                        "TestCaseID", "Mode", "ScanMode", "ItemID", "Qty");
//
//                for (ScanItemRow scan : block.getScanItems()) {
//                    System.out.printf("   %-10s %-10s %-10s %-15s %-10s%n",
//                            scan.getTestCaseId(),
//                            scan.getMode(),
//                            scan.getScanMode(),
//                            scan.getItemId(),
//                            scan.getQuantity());
//                }
//            }
//
//            // Select Items
//            if (!block.getSelectItems().isEmpty()) {
//                System.out.println("   --- Select Items ---");
//                System.out.printf("   %-10s %-15s %-10s %-10s%n",
//                        "TestCaseID", "ItemCode", "Qty", "UOM");
//
//                for (SelectItemRow sel : block.getSelectItems()) {
//                    System.out.printf("   %-10s %-15s %-10s %-10s%n",
//                            sel.getTestCaseId(),
//                            sel.getSearchKeyword1(),
//                            sel.getQuantity(),
//                            sel.getSearchValue1());
//                }
//            }
//
//            // Select Sets
//            if (!block.getSelectSets().isEmpty()) {
//                System.out.println("   --- Select Sets ---");
//                System.out.printf("   %-10s %-15s %-10s%n",
//                        "TestCaseID", "SetCode", "Qty");
//
//                for (SelectSetRow set : block.getSelectSets()) {
//                    System.out.printf("   %-10s %-15s%n",
//                            set.getTestCaseId(),
//                            set.getSetId());
//                }
//            }
//
//            System.out.println("--------------------------------------");
//        }
//    }
            goodsReceiveChallanAdd.clickCalculateCharge().clickSave().verifyDocumentSave();
            //SeleniumUtils.click(By.xpath("//span[text()='Calcula']"), WaitType.CLICKABLE, "Calculate Charge");
            //SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loader");
            //SeleniumUtils.click(By.xpath("//span[text()='ave']"), WaitType.CLICKABLE, "Receipts Save");
            //SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loader");
//            String DocumentNo = SeleniumActions.getText(By.xpath("//*[@id='messagebox-1001-msg']"));
//            System.out.println(DocumentNo);
//            Pattern pattern = Pattern.compile("\\b[A-Z]+/\\d+/\\d{2}-\\d{2}\\b");
//            Matcher matcher = pattern.matcher(DocumentNo);
//            String messagestatus = SeleniumActions.getText(By.id("messagebox-1001_header-title-textEl"));
//            if (messagestatus.equalsIgnoreCase("success")) {
//                System.out.println("document save status is success");
//            }
//            if (matcher.find()) {
//                System.out.println("Extracted Document No: " + matcher.group());
//            } else {
//                System.out.println("No match found.");
//            }
            DocumentMessageBox messageBox = new DocumentMessageBox();
            messageBox.clickOk();
            //SeleniumUtils.click(By.xpath("//span[text()='OK']"), WaitType.CLICKABLE, "Message Box Confirm");
//        if(vendorType.equalsIgnoreCase("Non-Consignment")){
//           System.out.println("Currently PI will not be generated so about to clicking No");
//      }
//            SeleniumUtils.click(By.xpath("//span[text()='No']"), WaitType.CLICKABLE, "Message Box Reject");
//            SeleniumUtils.click(By.xpath("//*[@class='mdi mdi-24px mdi-close']"), WaitType.CLICKABLE, "GRC Close");
//            SeleniumUtils.click(By.xpath("//span[text()='Yes']"), WaitType.CLICKABLE, "Discard Changes Yes");
            // Now control the reject/close flow
            if ("Non-Consignment".equalsIgnoreCase(vendorType) && "OS-OO-CM".equalsIgnoreCase(siteType)) {
                messageBox.rejectPI();
            }

            // closeGRC should always be executed
            messageBox.closeGRC();

            // Only do success validation if PI rejection was not triggered
//            if (!("Non-Consignment".equalsIgnoreCase(vendorType) && "OS-OO-CM".equalsIgnoreCase(siteType))) {
//                messageBox.handleSaveSuccess();
//            }


            // ✅ return to ReceiptsListPage for next DataProvider iteration
//            ReceiptsListPage receiptsListPage = NavigationUtils.navigateToProcurementSideMenu(homePage,
//                    MainMenuType.OPERATIONS,
//                    OperationsMenuType.RETURNS, ReceiptsListPage.class);
//            ReceiptsListPage receiptsListPage1 = new ReceiptsListPage();
//            return; // exit cleanly, test is ready for next iteration
//            receiptsListPage
//                    .clickAdd();
            HomePage homePage1=new HomePage();
            homePage1.logoutFromApplication();

        }
    }
}
