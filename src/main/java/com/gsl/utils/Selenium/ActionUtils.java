package com.gsl.utils.Selenium;

import com.gsl.driver.DriverManager;
import com.gsl.enums.WaitType;
import com.gsl.popup.PopupMonitor;
import com.gsl.reports.ReportLogger;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.WheelInput;

/**
 * Actions-API interaction helpers with built-in {@link ReportLogger} step logging and
 * {@link PopupMonitor} post-action popup checks — the wait/retry-free counterpart to
 * {@code SeleniumUtils}'s click/sendKeys methods, for interactions (hover, right-click,
 * double-click, drag-and-drop, wheel-scroll, modifier-key clicks) it doesn't cover.
 */
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

    /** Moves the mouse to {@code locator}'s element without clicking. */
    public static void moveToElement(By locator) {
        ReportLogger.uiAction("moveToElement {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().moveToElement(element).perform();
    }

    /** Natively scrolls {@code locator}'s element into view — Selenium 4's W3C "scroll" action
     *  (unlike a JS {@code scrollIntoView()} call, this walks up the element's actual scrolling-
     *  container chain, so it correctly reveals an element hidden by a nested horizontally-
     *  scrolling container, e.g. a wide ExtJS grid's own body scrollbar, not just the page/window
     *  scroll). No JavaScript execution involved. Safe to call even when the element is already
     *  fully visible — a no-op in that case. */
    public static void scrollToElement(By locator) {
        ReportLogger.uiAction("scrollToElement {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().scrollToElement(element).perform();
    }

    /** Right-clicks (context-clicks) {@code locator}'s element. */
    public static void rightClick(By locator) {
        ReportLogger.uiAction("rightClick {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().contextClick(element).perform();
        PopupMonitor.checkAfterAction("rightClick " + locator);
    }

    /** Double-clicks {@code locator}'s element. */
    public static void doubleClick(By locator) {
        ReportLogger.uiAction("doubleClick {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().doubleClick(element).perform();
        PopupMonitor.checkAfterAction("doubleClick " + locator);
    }

    /** Overload for a caller that already holds the {@link WebElement} (e.g. a row located via
     *  some other means) rather than a fresh {@link By} lookup. */
    public static void doubleClick(WebElement element) {
        ReportLogger.uiAction("doubleClick {}", element);
        getActions().doubleClick(element).perform();
        PopupMonitor.checkAfterAction("doubleClick " + element);
    }


    /** Presses and holds the mouse button down on {@code locator}'s element (no release). */
    public static void clickAndHold(By locator) {
        ReportLogger.uiAction("clickAndHold {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().clickAndHold(element).perform();
        PopupMonitor.checkAfterAction("clickAndHold " + locator);
    }

    /** Clears {@code locator}'s element then sends {@code keys} to it. */
    public static void sendKeys(By locator, CharSequence keys) {
        ReportLogger.uiAction("sendKeys {} = \"{}\"", locator, keys);
        WebElement element = DriverManager.getDriver().findElement(locator);
        element.clear();
        getActions().sendKeys(element, keys).perform();
        PopupMonitor.checkAfterAction("sendKeys " + locator);
    }

    /** Presses Escape (via keyboard, not targeted at any element) — used to dismiss a floating
     *  ExtJS menu/flyout that has no reliable "close" button of its own. */
    public static void pressEscape() {
        ReportLogger.uiAction("pressEscape");
        getActions().sendKeys(Keys.ESCAPE).perform();
    }

    /** Holds Shift while clicking — the modifier-key interaction ExtJS grids use to append a
     *  column to an existing multi-column sort instead of replacing it (see
     *  {@code GridColumnMenu.addSortAscending}/{@code addSortDescending}). */
    public static void shiftClick(By locator) {
        ReportLogger.uiAction("shiftClick {}", locator);
        WebElement element = DriverManager.getDriver().findElement(locator);
        getActions().keyDown(Keys.SHIFT).click(element).keyUp(Keys.SHIFT).perform();
        PopupMonitor.checkAfterAction("shiftClick " + locator);
    }

    /** Drags {@code sourceLocator}'s element and drops it onto {@code targetLocator}'s element. */
    public static void dragAndDrop(By sourceLocator, By targetLocator) {
        ReportLogger.uiAction("dragAndDrop {} → {}", sourceLocator, targetLocator);
        WebElement source = DriverManager.getDriver().findElement(sourceLocator);
        WebElement target = DriverManager.getDriver().findElement(targetLocator);
        getActions().dragAndDrop(source, target).perform();
        PopupMonitor.checkAfterAction("dragAndDrop " + sourceLocator + " -> " + targetLocator);
    }
}
