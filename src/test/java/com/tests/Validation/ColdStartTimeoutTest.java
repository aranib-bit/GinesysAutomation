package com.tests.Validation;

import com.gsl.driver.SessionPool;
import com.gsl.exceptions.UserPoolAcquisitionTimeoutException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Which session-creation failures earn one fresh cold start (browser-free). */
public class ColdStartTimeoutTest {

    @Test
    public void stalledDriverCommandsAreColdStartTimeouts() {
        assertTrue(SessionPool.isColdStartTimeout(new RuntimeException("wrap",
                new java.io.UncheckedIOException(new java.io.IOException(new java.util.concurrent.TimeoutException())))));
        assertTrue(SessionPool.isColdStartTimeout(new org.openqa.selenium.TimeoutException("page load")));
        assertTrue(SessionPool.isColdStartTimeout(new org.openqa.selenium.SessionNotCreatedException("no session")));
    }

    @Test
    public void poolAndTestProblemsAreNot() {
        assertFalse(SessionPool.isColdStartTimeout(new UserPoolAcquisitionTimeoutException("pool")));
        assertFalse(SessionPool.isColdStartTimeout(new IllegalStateException("bad data")));
        assertFalse(SessionPool.isColdStartTimeout(new org.testng.SkipException("no user")));
    }
}
