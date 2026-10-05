package com.gsl.driver.viewport;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import java.util.List;

/** Reads the page's real viewport size, shared by {@link com.gsl.driver.ViewportEnforcer} and the pinners. */
public final class ViewportReader {

    private ViewportReader() {}

    /** Reads {@code [window.innerWidth, window.innerHeight]} from the page via JS, as a 2-element {@code [width, height]} array. */
    @SuppressWarnings("unchecked")
    public static long[] read(WebDriver driver) {
        Object result = ((JavascriptExecutor) driver)
                .executeScript("return [window.innerWidth, window.innerHeight];");
        List<Number> dims = (List<Number>) result;
        return new long[] {dims.get(0).longValue(), dims.get(1).longValue()};
    }
}
