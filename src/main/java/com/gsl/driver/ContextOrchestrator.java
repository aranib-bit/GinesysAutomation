package com.gsl.driver;

import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.core.ApiSessionManager;
import com.gsl.config.ConfigFactory;
import com.gsl.driver.provider.AppiumProvider;
import com.gsl.enums.ApiPlatform;
import com.gsl.enums.PlatformType;
import com.gsl.enums.TestExecution;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.TestContextManager;
import org.openqa.selenium.WebDriver;

/**
 * Single entry point a {@code MultiContextTest} uses to move between execution contexts —
 * WEB, MOBILE, and API (itself sub-divided into WEBAPI/PUBLICAPI). Routes to whichever
 * underlying manager owns the surface ({@code DriverManager} for WEB/MOBILE, {@code
 * ApiSessionManager} for API) and logs every switch under {@code LogCategory.CONTEXT_SWITCH}.
 *
 * <p>{@code TestExecution.API} is inherently ambiguous on its own (WEBAPI or PUBLICAPI?), so it
 * always requires the two-arg overload; {@code WEB} and {@code MOBILE} have sensible one-arg
 * forms ({@code MOBILE} resolves to {@code mobile.defaultPlatform} from config unless a
 * {@link PlatformType} is given explicitly).
 */
public final class ContextOrchestrator {

    private static final ThreadLocal<String> lastLabel = new ThreadLocal<>();

    private ContextOrchestrator() {}

    /** WEB only — MOBILE/API need one of the overloads below since they require a sub-choice. */
    public static void switchTo(TestExecution execution) {
        if (execution == TestExecution.WEB) {
            switchToWeb();
        } else if (execution == TestExecution.MOBILE) {
            switchToMobile(ConfigFactory.getApiConfig().mobileDefaultPlatform());
        } else {
            throw new IllegalArgumentException(
                    "TestExecution.API is ambiguous on its own — call switchTo(TestExecution.API, ApiPlatform.WEBAPI/PUBLICAPI)");
        }
    }

    /** MOBILE with an explicit platform override (e.g. iOS regardless of the configured default). */
    public static void switchTo(TestExecution execution, PlatformType explicitMobilePlatform) {
        if (execution != TestExecution.MOBILE) {
            throw new IllegalArgumentException("switchTo(TestExecution, PlatformType) is for MOBILE only");
        }
        switchToMobile(explicitMobilePlatform);
    }

    /** API with its required sub-choice — WEBAPI or PUBLICAPI. */
    public static void switchTo(TestExecution execution, ApiPlatform apiPlatform) {
        if (execution != TestExecution.API) {
            throw new IllegalArgumentException("switchTo(TestExecution, ApiPlatform) is for API only");
        }
        if (apiPlatform == ApiPlatform.K6BULK) {
            throw new IllegalArgumentException("K6BULK is a one-shot backfill mechanism, not a switchable context");
        }

        String previous = lastLabel.get();
        ApiSessionManager.switchContext(apiPlatform);
        if (ApiSessionManager.getSession() == null) {
            ApiSessionManager.setSession(ApiSessionFactory.create(apiPlatform));
        }
        String label = "API:" + apiPlatform;
        logSwitch(previous, label);
    }

    /** Switches {@link DriverManager} to WEB, lazily calling {@link Driver#initDriver()} if no WEB driver exists yet. */
    private static void switchToWeb() {
        String previous = lastLabel.get();
        DriverManager.switchContext(PlatformType.WEB);
        if (DriverManager.getDriver() == null) {
            Driver.initDriver();
        }
        logSwitch(previous, "WEB");
    }

    /** Switches {@link DriverManager} to {@code mobilePlatform} (ANDROID/IOS only), lazily creating the Appium driver via {@link AppiumProvider} if none exists yet for that context. */
    private static void switchToMobile(PlatformType mobilePlatform) {
        if (mobilePlatform != PlatformType.ANDROID && mobilePlatform != PlatformType.IOS) {
            throw new IllegalArgumentException("MOBILE requires PlatformType.ANDROID or .IOS, got " + mobilePlatform);
        }
        String previous = lastLabel.get();
        DriverManager.switchContext(mobilePlatform);
        if (DriverManager.getDriver() == null) {
            // Calls AppiumProvider's explicit-platform overload directly — NOT
            // MasterDriverFactory.create(RunMode.MOBILE, browser), which only has the single-arg
            // ExecutionProvider.createDriver(Browser) and would silently ignore this override,
            // always falling back to config's mobile.defaultPlatform instead.
            WebDriver driver = new AppiumProvider().createDriver(mobilePlatform);
            DriverManager.setDriver(driver);
        }
        logSwitch(previous, "MOBILE:" + mobilePlatform);
    }

    /** Logs the context transition, updates {@link TestContextManager}'s active-surface label, and records {@code next} as the new {@link #current()}. */
    private static void logSwitch(String previous, String next) {
        ReportLogger.contextSwitch("[CONTEXT SWITCH] {} -> {}", previous == null ? "(none)" : previous, next);
        TestContextManager.setActiveSurface(next);
        lastLabel.set(next);
    }

    /** Returns the last switch's label, e.g. "WEB", "MOBILE:ANDROID", "API:WEBAPI". Null before any switch. */
    public static String current() {
        return lastLabel.get();
    }
}
