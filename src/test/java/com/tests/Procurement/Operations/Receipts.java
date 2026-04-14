package com.tests.Procurement.Operations;

import com.gsl.annotations.FrameworkAnnotation;
import com.gsl.constants.FrameworkConstants;
import com.gsl.driver.DriverManager;
import com.gsl.enums.TestAuthor;
import com.gsl.enums.WaitType;
import com.gsl.pages.HomePage;
import com.gsl.pages.LoginPage;
import com.gsl.pages.enums.Procurement.MainMenuType;
import com.gsl.pages.enums.Procurement.Operations.OperationsMenuType;
import com.gsl.reports.ExtentLogger;
import com.gsl.testdata.MasterTestData;
import com.gsl.pages.Procurement.Operations.ReceiptsListPage;
import com.gsl.utils.NavigationUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.WaitUtils;
import com.gsl.utils.dataprovider.MergedReceiptDataSupplier;
import com.tests.BaseTest;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Receipts extends BaseTest {

    @FrameworkAnnotation(TestAuthor.ARANI)
    @Test(description = "To check the creation of Receipts from UI", dataProvider = "getData", dataProviderClass = MergedReceiptDataSupplier.class)
    public void CreateReceipts(MasterTestData masterTestData) throws InterruptedException {
        // 1. Login to the application
        LoginPage loginPage = new LoginPage();
        HomePage homePage = loginPage.loginToApplication(masterTestData.getUsername(), masterTestData.getPassword());

        // 2. Use the navigation utility to get to the correct screen
        ReceiptsListPage receiptsListPage = NavigationUtils.navigateToProcurementSideMenu(homePage,
                MainMenuType.OPERATIONS,
                OperationsMenuType.RECEIPTS, ReceiptsListPage.class);


        //SeleniumUtils.click(By.xpath("//span[text()='Add']"),WaitType.CLICKABLE,"Receipts Add");
        //SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loading Mask");



        //SeleniumUtils.sendKeys(By.xpath("//span[text()='Receipt No.:']/ancestor::label/following-sibling::div//input[contains(@id, 'inputEl') and contains(@id,'combo')]"),WaitType.CLICKABLE,"Delivery Challan","Doc Scheme Selection");

        //get_driver().findElement(By.xpath("//div[@id='combo-1211-trigger-picker']")).click();
        //WebDriverWait wait = new WebDriverWait(get_driver(), Duration.ofSeconds(10));
        //WebElement tablevisible = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@class='headerBoundList']//table[contains(@class,'lookup_head')]")));

//        
        //SeleniumUtils.click(By.xpath("//div[text()='GRC' and contains(@class, 'comboflow')]"), WaitType.CLICKABLE,"Scheme Doc Selection");
        //SeleniumUtils.click(By.xpath("//div[@class='x-boundlist-item x-boundlist-item-over']/table/tbody/tr/td"), WaitType.CLICKABLE, "Scheme Doc Selection");
//        SeleniumUtils.sendKeys(By.xpath("//span[text()='Vendor Name:']/ancestor::label/following-sibling::div//input[contains(@id, 'grc_cbo_VendorName')]"), "ABUL KASHEM MOLLHA", "Vendor Selection");
//        SeleniumUtils.click(By.xpath("//div[@class='x-boundlist-item x-boundlist-item-over']/table/tbody/tr/td"), WaitType.CLICKABLE, "Vendor Selection");
        //String VendorType=SeleniumActions.getAttribute(By.xpath("//*[@id='grc_cbo_GrcType']"),"value");
        //System.out.println("Vendor Type extracted as "+VendorType);
        //get_driver().findElement(By.xpath("//span[text()='Vendor Name:']/ancestor::label/following-sibling::div//input[contains(@id, 'grc_cbo_VendorName')]")).sendKeys("AARUCHI FASHIONS PRIVATE LIMITED");
        //get_driver().findElement(By.xpath("//div[text()='AARUCHI FASHIONS PRIVATE LIMITED' and contains(@class, 'comboflow')]")).click();

        //SeleniumActions.scrollToElement(By.xpath("//div[text()='Item Details']"));
        //SeleniumUtils.click(By.xpath("//*[@class='mdi mdi-20px mdi-dots-horizontal']"), WaitType.CLICKABLE, "Item Population List Selector");
        //Thread.sleep(1000);
        WebDriverWait wait = new WebDriverWait(get_driver(), Duration.ofSeconds(10));
        WebElement menuOption = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//span[text()='Scan Item' and contains(@class, 'toolbar-small')]")));

        //SeleniumActions.moveToElementAndClick(By.xpath("//span[text()='Scan Item' and contains(@class, 'toolbar-small')]"));
        //JavascriptExecutorUtils.waitForExtJS();
        //SeleniumUtils.click(By.xpath("//span[text()='Scan Item' and contains(@class, 'toolbar-small')]"),WaitType.CLICKABLE,"Scan Item Selection");
        //Thread.sleep(1000);
        WebElement currentelement = get_driver().switchTo().activeElement();
        System.out.println(currentelement);
        //System.out.println(frame);
        //boolean txtScanCodeStatus = isComponentReady("txtScanCode");
        //System.out.println("Status Code " +txtScanCodeStatus);
//        Boolean isMasked = (Boolean) ((JavascriptExecutor) get_driver()).executeScript(
//                "var cmp = Ext.ComponentQuery.query('#txtScanCode')[0];" +
//                        "return !!(cmp && cmp.up('panel') && cmp.up('panel').isMasked && cmp.up('panel').isMasked());"
//        );
//        System.out.println("Masked: " + isMasked);
//        if(txtScanCodeStatus==false){HE10151
//            System.out.println("rendered status false");
//        }else{
        //Thread.sleep(90000);
        System.out.println("wait over");
        String popupheader = SeleniumActions.getText(By.xpath("//div[@class='winTitleType2 winTitleType2-default']"));
        System.out.println(popupheader);

        // Use a single, robust locator for the input field
        By itemScanInput = By.xpath("//input[contains(@name, 'textfield-') and contains(@class, 'x-form-field')]");

        // --- First Item ---
        WaitUtils.fluentWait(itemScanInput);
        SeleniumActions.moveToElementAndSendKeys(itemScanInput, "HE10151");
        System.out.println("before first enter");
        SeleniumActions.sendKeyToElement(itemScanInput, Keys.ENTER);

        // Wait for ExtJS to process the first item and for the UI to be ready
        System.out.println("Waiting for ExtJS to process first item...");
        JavascriptExecutorUtils.waitForExtJS();
        System.out.println("ExtJS is ready for the next item.");

        // --- Second Item ---
        System.out.println("Before 2nd item");
        WaitUtils.fluentWait(itemScanInput); // Wait for the input to be clickable
        SeleniumActions.moveToElementAndSendKeys(itemScanInput, "HE1052");

        System.out.println("before second enter");
        SeleniumActions.sendKeyToElement(itemScanInput, Keys.ENTER);
        System.out.println("After 2nd item enter");

        // Wait for ExtJS to process the second item
        JavascriptExecutorUtils.waitForExtJS();
        System.out.println("click enter done");
        //System.out.println("wait over before accept");
        //SeleniumUtils.click(By.xpath("//span[text()='ccept']"), WaitType.CLICKABLE, "Item Accept");
        //SeleniumUtils.click(By.xpath("//span[text()='ancel']"), WaitType.CLICKABLE, "Scan Item Cancel");
        SeleniumUtils.click(By.xpath("//span[text()='Calcula']"), WaitType.CLICKABLE, "Calculate Charge");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loader");
        SeleniumUtils.click(By.xpath("//span[text()='ave']"), WaitType.CLICKABLE, "Receipts Save");
        SeleniumUtils.elementDisappear(FrameworkConstants.getLoadingMask(), "Loader");
        String DocumentNo = SeleniumActions.getText(By.xpath("//*[@id='messagebox-1001-msg']"));
        System.out.println(DocumentNo);
        Pattern pattern = Pattern.compile("\\b[A-Z]+/\\d+/\\d{2}-\\d{2}\\b");
        Matcher matcher = pattern.matcher(DocumentNo);
        String messagestatus = SeleniumActions.getText(By.id("messagebox-1001_header-title-textEl"));
        if(messagestatus.equalsIgnoreCase("success")){
            System.out.println("document save status is success");
        }
        if (matcher.find()) {
            System.out.println("Extracted Document No: " + matcher.group());
            ExtentLogger.pass("Extracted Document No: " + matcher.group());
        } else {
            System.out.println("No match found.");
        }

        SeleniumUtils.click(By.xpath("//span[text()='OK']"),WaitType.CLICKABLE,"Message Box Confirm");
//        if(VendorType.equalsIgnoreCase("Non-Consignment")){
//            System.out.println("Currently PI will not be generated so about to clicking No");
//        }
        SeleniumUtils.click(By.xpath("//span[text()='No']"),WaitType.CLICKABLE,"Message Box Reject");
        SeleniumUtils.click(By.xpath("//*[@class='mdi mdi-24px mdi-close']"),WaitType.CLICKABLE,"GRC Close");
        SeleniumUtils.click(By.xpath("//span[text()='Yes']"),WaitType.CLICKABLE,"Discard Changes Yes");
    }

//    public boolean isComponentReady(String itemId) {
//        String script =
//                "var isReady = function(itemId) {" +
//                        "  var cmp = Ext.ComponentQuery.query('#' + itemId)[0];" +
//                        "  return !!(cmp && cmp.rendered && cmp.inputEl && cmp.inputEl.dom && !cmp.disabled && cmp.inputEl.isVisible());" +
//                        "};" +
//                        "return isReady(arguments[0]);";
//
//        return (Boolean) ((JavascriptExecutor) get_driver()).executeScript(script, itemId);
//    }

    private WebDriver get_driver() {
        // Implementation details for getting the WebDriver instance
        // For example, you might want to use a Singleton pattern
        // to ensure that there is only one instance of the WebDriver
        // throughout the test execution.
        return DriverManager.getDriver();
    }
}
