package com.tests.Validation;

import com.gsl.aspects.LoggingAspect;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** An exception is reported (with a screenshot) once, not again by every outer frame (run 20261009_164937). */
public class ReportOnceTest {

    @Test
    public void sameExceptionOrWrapperIsAlreadyReported() {
        RuntimeException reported = new RuntimeException("login form missing");
        assertTrue(LoggingAspect.isSameOrWraps(reported, reported));
        assertTrue(LoggingAspect.isSameOrWraps(new IllegalStateException("wrap", reported), reported));
    }

    @Test
    public void differentExceptionIsNew() {
        RuntimeException reported = new RuntimeException("first");
        assertFalse(LoggingAspect.isSameOrWraps(new RuntimeException("second"), reported));
        assertFalse(LoggingAspect.isSameOrWraps(new RuntimeException("first"), null));
    }
}
