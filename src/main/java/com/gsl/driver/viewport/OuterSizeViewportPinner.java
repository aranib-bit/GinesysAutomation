package com.gsl.driver.viewport;

import com.gsl.utils.Log;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;

/**
 * Last-resort pinner for a driver with neither CDP nor BiDi (and the fallback when the BiDi pin
 * fails): the outer window includes browser chrome, so the inner viewport is smaller than what
 * {@code window().setSize()} asked for. Measures that overhead from the live page and grows the
 * outer window by exactly that much — nothing is hardcoded.
 */
public final class OuterSizeViewportPinner implements ViewportPinner {

    @Override
    public void pin(WebDriver driver, int width, int height) {
        long[] inner = ViewportReader.read(driver);
        Dimension outer = driver.manage().window().getSize();
        int dw = (int) (width - inner[0]);
        int dh = (int) (height - inner[1]);
        Log.info("[ViewportEnforcer] Outer-size compensation — outer={}, viewport={}x{}, overhead={}x{}",
                outer, inner[0], inner[1], dw, dh);
        if (dw == 0 && dh == 0) return;
        driver.manage().window().setSize(new Dimension(outer.getWidth() + dw, outer.getHeight() + dh));
    }

    @Override
    public String protocolName() {
        return "OUTER_SIZE";
    }
}
