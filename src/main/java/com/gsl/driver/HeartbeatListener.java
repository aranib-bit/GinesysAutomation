package com.gsl.driver;

import com.gsl.testdata.ExecutionSet;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import org.openqa.selenium.support.events.WebDriverListener;

import java.lang.reflect.Method;

/**
 * Selenium {@link WebDriverListener} that feeds {@link UserPool#heartbeat} on every
 * browser interaction. Wired in {@link DriverManager#setDriver(org.openqa.selenium.WebDriver)}
 * via {@code EventFiringDecorator}, so every WebDriver/WebElement/Navigation/TargetLocator
 * call from any provider (Chrome/Firefox/Edge/Remote/Grid/BrowserStack/LambdaTest) refreshes
 * the holder's heartbeat.
 *
 * <p>The starvation detector in {@link UserPool} only flags a holder once its heartbeat
 * stops advancing. A test that is actively clicking, typing, or waiting on the browser
 * keeps the heartbeat fresh and never triggers the warning, regardless of how long the
 * test runs in total.</p>
 */
public final class HeartbeatListener implements WebDriverListener {

    /**
     * Fires before every intercepted WebDriver/WebElement/etc. call; refreshes the heartbeat for
     * the current thread's UI user, but only when that user is a SET1/UI-type credential —
     * API-only or non-pool credentials are silently skipped since {@link UserPool} starvation
     * detection only tracks SET1 UI holders.
     */
    @Override
    public void beforeAnyCall(Object target, Method method, Object[] args) {
        UserCredential user = DriverManager.getCurrentUser();
        if (user == null) return;
        if (user.executionSet() != ExecutionSet.SET1) return;
        if (user.userType() != UserType.UI) return;
        UserPool.heartbeat(UserType.UI, user.username());
    }
}
