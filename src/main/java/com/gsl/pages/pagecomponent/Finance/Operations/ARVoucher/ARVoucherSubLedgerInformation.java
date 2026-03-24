package com.gsl.pages.pagecomponent.Finance.Operations.ARVoucher;

import com.google.common.util.concurrent.Uninterruptibles;
import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.utils.Selenium.ActionUtils;
import com.gsl.utils.Selenium.JavascriptExecutorUtils;
import com.gsl.utils.Selenium.SeleniumActions;
import com.gsl.utils.Selenium.SeleniumUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.gsl.utils.Selenium.ExtJsUtils.setExtJsComboByName;

public class ARVoucherSubLedgerInformation {
    private static final By SUBLEDGERNAMEBLANK=By.xpath("(//div[contains(@class,'x-window')]//table[contains(@class,'x-grid-item')])[last()]//tr[last()]//td[3]");
    private static final String SUBLEDGERSELECTION_XPATH="//span[text()='%s']";
    private static final By SUBLEDGERAMOUNTCR = By.xpath("//tr[.//td/div[normalize-space()='ABUL KASHEM MOLLHA'] and .//td/div[normalize-space()='Hena Embroidery']]//td[contains(@data-columnid,'numbercolumn')]//div");
    private static final By SUBLEDGERADJUSTBUTTON = By.xpath("//tr[      td[@data-columnid=//span[normalize-space()='Sub Ledger']/ancestor::div[contains(@id,'gridcolumn')]/@id]          /div[normalize-space()='ABUL KASHEM MOLLHA']      and      td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]          /div[normalize-space()='Hena Embroidery']    ]    //td[@data-columnid=//span[normalize-space()='Reference Site']/ancestor::div[contains(@id,'gridcolumn')]/@id]    /following-sibling::td        /div//button[normalize-space()='Adjust']");


    public ARVoucherSubLedgerInformation setSubLedgerName1(String subledgerName) {
        // 🔑 USE STRING TEMPLATE HERE
        SeleniumUtils.click(
                SUBLEDGERNAMEBLANK,
                WaitType.CLICKABLE,
                "SUBLEDGER NAME BLANK"
        );
        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
        System.out.println("After Sub Ledger Blank Row Click");

        setExtJsComboByName("subLedgerName", subledgerName);



        //SeleniumUtils.click(SUBLEDGERNAMEBLANK,WaitType.CLICKABLE,"SUBLEDGERNAMEBLANK");
        //SeleniumUtils.sendKeys(SUBLEDGERNAMEBLANK,WaitType.CLICKABLE,subledgerName,"SUBLEDGERNAMEBLANK");
        System.out.println("After Sub Ledger Set Value");
        //SeleniumUtils.click(getSubLedgerNameSelection(subledgerName), WaitType.CLICKABLE, "SUBLEDGERSELECTION_XPATH");
        return this;
    }

    public ARVoucherSubLedgerInformation setSubLedgerName(String subledgerName) {
//        // 🔑 USE STRING TEMPLATE HERE
//        SeleniumUtils.click(
//                SUBLEDGERNAMEBLANK,
//                WaitType.CLICKABLE,
//                "SUBLEDGER NAME BLANK"
//        );
//        Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);




        WebDriver driver = DriverManager.getDriver();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        System.out.println("=== STEP 1 : START Set Sub Ledger for Row 0 ===");
        JavascriptExecutorUtils.setSubLedger(driver, subledgerName);
        System.out.println("=== STEP 2 : setSubLedger completed ===");
        //JavascriptExecutorUtils.printAllExtJsComboDetails();

        //JavascriptExecutorUtils.printExtJsGridColumns();
        //JavascriptExecutorUtils.getExtJsGridColumns();
        //JavascriptExecutorUtils.detectColumnAndCombo();
        //JavascriptExecutorUtils.printGridColumns(driver);
        //JavascriptExecutorUtils.printEditableColumns(driver);
        //JavascriptExecutorUtils.diagnoseAllGrids(driver);
        //JavascriptExecutorUtils.diagnoseReferenceSite(driver);
        //JavascriptExecutorUtils.diagnoseRowEditorFields(driver,0);
        //JavascriptExecutorUtils.setSubLedger( DriverManager.getDriver(),
          //      "ABUL KASHEM MOLLHA" );
        //JavascriptExecutorUtils.setSubLedgerName(driver, 0, "ABUL KASHEM MOLLHA");


        return this;
    }

    public ARVoucherSubLedgerInformation setSubLedgerAmountold(String subLedgerAmount) {
        System.out.println("Before double click");
        ActionUtils.doubleClick(SUBLEDGERAMOUNTCR);
        //SeleniumUtils.click(SUBLEDGERAMOUNTCR,WaitType.CLICKABLE,"SUBLEDGERAMOUNTCR");
        System.out.println("After double click");
        //System.out.println("Before clear");
        //SeleniumUtils.clear(SUBLEDGERAMOUNTCR,"SUBLEDGERAMOUNTCR");
        SeleniumUtils.sendKeys(SUBLEDGERAMOUNTCR,subLedgerAmount,"SUBLEDGERAMOUNTCR");
        return this;

    }


    public ARVoucherSubLedgerInformation setSubLedgerAmount(String subLedgerAmount) throws InterruptedException {

        // Step 1: Double-click the cell div to activate the editor
        ActionUtils.doubleClick(SUBLEDGERAMOUNTCR);

        // Step 2: Wait briefly for ExtJS editor to activate
        Thread.sleep(500); // or replace with your custom wait utility

        // Step 3: Locate the input using WebDriverWait directly
        By activeEditorInput = By.xpath(
                "//tr[.//td/div[normalize-space()='ABUL KASHEM MOLLHA'] " +
                        "and .//td/div[normalize-space()='Hena Embroidery']]" +
                        "//td[contains(@data-columnid,'numbercolumn')]//input"
        );

        WebElement inputField = new WebDriverWait(DriverManager.getDriver(), Duration.ofSeconds(10))
                .until(ExpectedConditions.visibilityOfElementLocated(activeEditorInput));

        // Step 4: Set value via JS + fire ExtJS events
        JavascriptExecutor js = (JavascriptExecutor) DriverManager.getDriver();
        js.executeScript(
                "var el = arguments[0];" +
                        "var nativeInputValueSetter = Object.getOwnPropertyDescriptor(" +
                        "    window.HTMLInputElement.prototype, 'value').set;" +
                        "nativeInputValueSetter.call(el, arguments[1]);" +
                        "el.dispatchEvent(new Event('input',  { bubbles: true }));" +
                        "el.dispatchEvent(new Event('change', { bubbles: true }));",
                inputField, subLedgerAmount
        );

        System.out.println("SubLedger amount set via JS: " + subLedgerAmount);
        return this;
    }

    public void clickAdjust(){
        SeleniumUtils.click(SUBLEDGERADJUSTBUTTON,WaitType.CLICKABLE,"SUBLEDGERADJUSTBUTTON");
    }


    private By getSubLedgerNameSelection(String subLedgerName) {
        String xpath = String.format(SUBLEDGERSELECTION_XPATH, subLedgerName);
        System.out.println("Generated XPath: " + xpath); // <-- Print it
        return By.xpath(xpath);
    }

}
