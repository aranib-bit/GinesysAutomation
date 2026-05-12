package com.gsl.popup;

import org.openqa.selenium.By;

/**
 * Centralized locators for the application's ExtJS messagebox.
 * IDs are stable across sessions in this application (messagebox-1001 pattern).
 * These match the IDs already used by DocumentMessageBox in the pages layer.
 */
final class AppPopupLocators {

    // Outer messagebox container — only visible when a dialog is active
    static final By MESSAGEBOX_CONTAINER = By.xpath(
            "//div[contains(@class,'x-message-box') and not(contains(@class,'x-hide-display'))]");

    // Header title (e.g., "Success", "Error", "Adjustment Failed")
    static final By MESSAGEBOX_HEADER = By.id("messagebox-1001_header-title-textEl");

    // Body message text
    static final By MESSAGEBOX_BODY = By.id("messagebox-1001-msg");

    // Primary dismiss button — OK or Close, whichever is present
    static final By MESSAGEBOX_DISMISS_BTN = By.xpath(
            "//div[contains(@class,'x-message-box')]//span[text()='OK' or text()='Close']");

    private AppPopupLocators() {}
}
