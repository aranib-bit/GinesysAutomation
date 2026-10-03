package com.tests.config;

import com.gsl.enums.TargetOs;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Pure-logic checks for {@link TargetOs}; no browser or Grid needed. */
public class TargetOsTest {

    @Test
    public void blankAndNullDefaultToLinux() {
        Assert.assertEquals(TargetOs.parse(null), TargetOs.LINUX);
        Assert.assertEquals(TargetOs.parse("  "), TargetOs.LINUX);
    }

    @Test
    public void parsesCaseInsensitively() {
        Assert.assertEquals(TargetOs.parse("Windows"), TargetOs.WINDOWS);
        Assert.assertEquals(TargetOs.parse(" LINUX "), TargetOs.LINUX);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void unknownValueFailsFast() {
        TargetOs.parse("macos");
    }

    @Test
    public void platformsMatchTheRequestedOs() {
        Assert.assertEquals(TargetOs.WINDOWS.platform().toString(), "windows");
        Assert.assertEquals(TargetOs.LINUX.platform().toString(), "linux");
    }
}
