package com.gsl.popup;

import org.openqa.selenium.By;

/**
 * Centralized locators for the application's ExtJS messagebox — the single popup dialog that
 * {@link PopupHandler} probes for and {@link PopupMonitor} decides how to react to.
 * IDs are stable across sessions in this application (the {@code messagebox-1001} pattern), and
 * these match the IDs already used by {@code DocumentMessageBox} in the pages layer.
 *
 * <p>Package-private — only {@link PopupHandler} should reference these locators directly; other
 * code should go through {@link PopupMonitor}'s higher-level API instead.
 */
final class AppPopupLocators {

    // Outer messagebox container — role=alertdialog is only set on the actively displayed dialog,
    // disambiguating from hidden x-message-box shells ExtJS keeps in the DOM
    static final By MESSAGEBOX_CONTAINER = By.xpath(
            "//div[@role='alertdialog'"
            + " and contains(@class,'x-message-box')"
            + " and not(contains(@class,'x-hide-display'))]");

    // Header title (e.g., "Success", "Error", "Adjustment Failed")
    static final By MESSAGEBOX_HEADER = By.id("messagebox-1001_header-title-textEl");

    // Body message text
    static final By MESSAGEBOX_BODY = By.id("messagebox-1001-msg");

    // Primary dismiss button — OK or Close, whichever is present
    static final By MESSAGEBOX_DISMISS_BTN = By.xpath(
            "//div[contains(@class,'x-message-box')]//span[text()='OK' or text()='Close']");

    // All visible action buttons inside the messagebox — 2+ means page flow must choose
    static final By MESSAGEBOX_ACTION_BTNS = By.xpath(
            "//div[contains(@class,'x-message-box')]//a[contains(@class,'x-btn')]");

    private AppPopupLocators() {}
}
