package com.gsl.constants;

import org.openqa.selenium.By;

public final class FrameworkConstants {

    private FrameworkConstants() {
    }

    private static final String REPORT_PATH = System.getProperty("user.dir") + "/index.html";
    private static final By BY_LOADING_WINDOW = By.xpath("//div[@class='x-window vport x-layer x-window-default x-border-box x-window-maximized']");
    private static final By BY_LOADING_MASK = By.xpath("//div[contains(@class, 'x-mask-fixed')]");

    public static String getReportPath() {
        return REPORT_PATH;
    }

    public static By getLoadingWindow() {
        return BY_LOADING_WINDOW;
    }

    public static By getLoadingMask() {
        return BY_LOADING_MASK;
    }
}
