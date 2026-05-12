package com.gsl.driver;

import com.gsl.enums.PlatformType;
import lombok.Builder;
import lombok.Getter;

/**
 * Immutable data carrier for Appium/mobile driver creation requirements.
 * Passed into AndroidManager and IOSManager so capability setup is self-contained.
 *
 * Build via:
 *   MobileDriverData data = MobileDriverData.builder()
 *       .platformType(PlatformType.ANDROID)
 *       .deviceName("Pixel 6")
 *       .platformVersion("13")
 *       .appiumUrl("http://localhost:4723")
 *       .app("/path/to/app.apk")
 *       .automationName("UiAutomator2")
 *       .build();
 */
@Getter
@Builder
public final class MobileDriverData {

    private final PlatformType platformType;
    private final String       deviceName;
    private final String       platformVersion;
    private final String       appiumUrl;
    private final String       app;
    private final String       automationName;
    private final String       bundleId;
}
