package com.gsl.constants;

import org.openqa.selenium.By;

import java.nio.file.Paths;

public final class FrameworkConstants {

    private FrameworkConstants() {
    }

    private static String reportPath;
    private static final By BY_LOADING_WINDOW = By.xpath("//div[@class='x-window vport x-layer x-window-default x-border-box x-window-maximized']");
    private static final By BY_LOADING_MASK = By.xpath("//div[contains(@class, 'x-mask-fixed')]");
    private static final By BY_SELECT_DOC_GRID_LOADER = By.xpath(
            "//div[contains(@class,'x-window') and not(contains(@class,'x-window-maximized'))]" +
            "//div[@role='progressbar' and @aria-valuetext='Loading...']");

    public static String getReportPath() {
        if (reportPath == null) {
            String ts = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            reportPath = System.getProperty("user.dir") + "/index_" + ts + ".html";
        }
        return reportPath;
    }

    public static String getReportDir() {
        return Paths.get(getReportPath()).getParent().toString();
    }

    public static By getLoadingWindow() {
        return BY_LOADING_WINDOW;
    }

    public static By getLoadingMask() {
        return BY_LOADING_MASK;
    }

    public static By getSelectDocGridLoader() {
        return BY_SELECT_DOC_GRID_LOADER;
    }
}
