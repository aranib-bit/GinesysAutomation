package com.gsl.config;

import com.gsl.config.converter.StringToBrowserConverter;
import com.gsl.config.converter.StringToRunModeConverter;
import com.gsl.enums.Browser;
import com.gsl.enums.RunMode;
import org.aeonbits.owner.Config;

/**
 * Browser/driver launch configuration — browser choice, run mode (local/remote/grid), window
 * size, viewport enforcement, network capture mode, and remote-grid/cloud-provider (Selenium
 * Grid, BrowserStack, LambdaTest) credentials. Read from {@code browser.properties}, merged with
 * {@code system:properties} so any key can be overridden per run with {@code -Dkey=value}.
 * Consumed by {@code com.gsl.driver} to build the WebDriver instance.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/browser.properties",
        "file:${user.dir}/src/test/resources/config/browser.properties"
})
public interface BrowserConfig extends Config {

    /** Raw string — preserved for backward compat. Prefer browserType() for typed access. */
    @DefaultValue("chrome")
    String browser();

    /** Raw string — preserved for backward compat. Prefer runMode() for typed access. */
    @DefaultValue("local")
    String runmode();

    /**
     * Typed Browser enum converted from the browser property via StringToBrowserConverter.
     * DefaultValue is required here: with "system:properties" as a co-source, Owner 1.0.12
     * does not fall back to the file source when the key is absent from system properties —
     * it resolves to null and skips the converter entirely, bypassing its own blank/null fallback.
     */
    @Key("browser")
    @DefaultValue("chrome")
    @Config.ConverterClass(StringToBrowserConverter.class)
    Browser browserType();

    /** Typed RunMode enum converted from the runmode property via StringToRunModeConverter. Same DefaultValue caveat as browserType(). */
    @Key("runmode")
    @DefaultValue("local")
    @Config.ConverterClass(StringToRunModeConverter.class)
    RunMode runMode();

    /** Remote WebDriver server URL, used when {@link #runMode()} is a remote mode. */
    String remoteurl();

    /** Whether the browser launches headless (no visible window). */
    @Key("headless")
    @DefaultValue("false")
    boolean headless();

    /** Whether the browser launches in incognito/private mode. */
    @Key("incognito")
    @DefaultValue("false")
    boolean incognito();

    /** Whether the browser accepts insecure/self-signed TLS certificates. */
    @Key("acceptInsecureCerts")
    @DefaultValue("false")
    boolean acceptInsecureCerts();

    /** Standardized window width used across all environments/browsers — see {@link #windowHeight()}. */
    @Key("browser.window.width")
    @DefaultValue("1366")
    int windowWidth();

    @Key("browser.window.height")
    @DefaultValue("768")
    int windowHeight();

    /** Explicit override for whether {@code ViewportEnforcer} runs at all. Deliberately no
     *  {@code @DefaultValue} — {@code null} (nothing set in system properties or
     *  browser.properties) means "use the run-mode default" (enabled for remote/grid modes,
     *  disabled for {@code LOCAL} — see {@code ViewportEnforcer#isEnabled}). Set
     *  {@code -Dviewport.enforcer.enabled=true} or {@code =false} to force either way regardless
     *  of runmode, e.g. to re-enable strict local enforcement for a one-off debug session. */
    @Key("viewport.enforcer.enabled")
    Boolean viewportEnforcerEnabled();

    /** Network-capture transport (e.g. CDP) used for browser-log/network diagnostics. */
    @Key("network.mode")
    @DefaultValue("CDP")
    String networkMode();

    /** Selenium Grid hub URL. */
    @Key("grid.url")
    @DefaultValue("http://localhost:4444/wd/hub")
    String gridUrl();

    /** Selenium Grid hub URL to use when running inside Docker. */
    @Key("docker.grid.url")
    @DefaultValue("http://localhost:4444/wd/hub")
    String dockerGridUrl();

    /** BrowserStack account username. */
    @Key("browserstack.username")
    @DefaultValue("")
    String browserStackUsername();

    /** BrowserStack access key. */
    @Key("browserstack.accesskey")
    @DefaultValue("")
    String browserStackAccessKey();

    /** Build name reported to BrowserStack for grouping runs. */
    @Key("browserstack.build.name")
    @DefaultValue("CI Build")
    String browserStackBuildName();

    /** LambdaTest account username. */
    @Key("lambdatest.username")
    @DefaultValue("")
    String lambdaTestUsername();

    /** LambdaTest access key. */
    @Key("lambdatest.accesskey")
    @DefaultValue("")
    String lambdaTestAccessKey();

    /** Type of application under test (e.g. extjs), selecting app-specific wait/interaction strategies. */
    @Key("application.type")
    @DefaultValue("extjs")
    String applicationType();
}
