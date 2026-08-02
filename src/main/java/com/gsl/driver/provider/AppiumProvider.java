package com.gsl.driver.provider;

import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.MobileDriverData;
import com.gsl.driver.manager.AndroidManager;
import com.gsl.driver.manager.IOSManager;
import com.gsl.enums.Browser;
import com.gsl.enums.PlatformType;
import org.openqa.selenium.WebDriver;

/**
 * Appium driver provider for mobile execution. Builds {@link MobileDriverData} from config
 * (mobile.* keys in api.properties) and dispatches to {@link AndroidManager}/{@link IOSManager}
 * — both already fully implemented, this class only needed its body wired to them.
 * {@code mobile.defaultPlatform} decides ANDROID vs IOS; {@code ContextOrchestrator}'s explicit
 * {@code switchTo(TestExecution.MOBILE, PlatformType)} overload can override it per test.
 */
public final class AppiumProvider implements ExecutionProvider {

    @Override
    public WebDriver createDriver(Browser browser) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        PlatformType platform = cfg.mobileDefaultPlatform();
        return createDriver(platform);
    }

    public WebDriver createDriver(PlatformType platform) {
        ApiConfig cfg = ConfigFactory.getApiConfig();

        MobileDriverData data = MobileDriverData.builder()
                .platformType(platform)
                .deviceName(cfg.mobileDeviceName())
                .platformVersion(cfg.mobilePlatformVersion())
                .appiumUrl(cfg.mobileAppiumUrl())
                .app(cfg.mobileApp())
                .automationName(cfg.mobileAutomationName())
                .bundleId(cfg.mobileBundleId())
                .build();

        return switch (platform) {
            case ANDROID -> AndroidManager.getDriver(data);
            case IOS -> IOSManager.getDriver(data);
            case WEB -> throw new IllegalArgumentException("[AppiumProvider] WEB is not a mobile platform");
        };
    }

    @Override
    public String getProviderName() {
        return "AppiumProvider";
    }
}
