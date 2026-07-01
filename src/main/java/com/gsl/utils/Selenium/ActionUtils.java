package com.gsl.utils.Selenium;

import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.reports.ReportLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.WheelInput;

public class ActionUtils {

    private static Actions getActions() {
        return new Actions(DriverManager.getDriver());
    }

    /**
     * Dispatches a trusted W3C wheel event from the given origin element.
     * Used by SelectDocumentPopup to scroll an ExtJS BufferedStore grid in a
     * way that fires the renderer's scroll listeners (a synthetic JS event
     * on scrollTop= is untrusted and may be ignored, leaving pages unloaded).
     */
    public static void scrollByWheel(WebElement origin, int deltaY) {
        ReportLogger.uiAction("scrollByWheel deltaY={}", deltaY);
        getActions()
                .scrollFromOrigin(WheelInput.ScrollOrigin.fromElement(origin), 0, deltaY)
                .perform();
    }

    public static void moveToElement(By locator) {
        ReportLogger.uiAction("moveToElement {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().moveToElement(element).perform();
    }

    public static void rightClick(By locator) {
        ReportLogger.uiAction("rightClick {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().contextClick(element).perform();
    }

    public static void doubleClick(By locator) {
        ReportLogger.uiAction("doubleClick {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().doubleClick(element).perform();
    }


    public static void clickAndHold(By locator) {
        ReportLogger.uiAction("clickAndHold {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().clickAndHold(element).perform();
    }

    public static void sendKeys(By locator, CharSequence keys) {
        ReportLogger.uiAction("sendKeys {} = \"{}\"", locator, keys);
        WebElement element = DriverManager.getDriver().findElement(locator);
        element.clear();
        getActions().sendKeys(element, keys).perform();
    }

    public static void dragAndDrop(By sourceLocator, By targetLocator) {
        ReportLogger.uiAction("dragAndDrop {} → {}", sourceLocator, targetLocator);
        WebElement source = DriverManager.getDriver().findElement(sourceLocator);
        WebElement target = DriverManager.getDriver().findElement(targetLocator);
        getActions().dragAndDrop(source, target).perform();
    }
}
