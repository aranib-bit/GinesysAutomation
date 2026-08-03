package com.gsl.config;

import com.gsl.config.converter.StringToBrowserConverter;
import com.gsl.config.converter.StringToRunModeConverter;
import com.gsl.enums.Browser;
import com.gsl.enums.RunMode;
import org.aeonbits.owner.Config;

@Config.Sources({"system:properties", "file:${user.dir}/src/test/resources/config/browser.properties"})
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

    String remoteurl();

    @Key("headless")
    @DefaultValue("false")
    boolean headless();

    @Key("incognito")
    @DefaultValue("false")
    boolean incognito();

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

    @Key("network.mode")
    @DefaultValue("CDP")
    String networkMode();

    @Key("grid.url")
    @DefaultValue("http://localhost:4444/wd/hub")
    String gridUrl();

    @Key("docker.grid.url")
    @DefaultValue("http://localhost:4444/wd/hub")
    String dockerGridUrl();

    @Key("browserstack.username")
    @DefaultValue("")
    String browserStackUsername();

    @Key("browserstack.accesskey")
    @DefaultValue("")
    String browserStackAccessKey();

    @Key("browserstack.build.name")
    @DefaultValue("CI Build")
    String browserStackBuildName();

    @Key("lambdatest.username")
    @DefaultValue("")
    String lambdaTestUsername();

    @Key("lambdatest.accesskey")
    @DefaultValue("")
    String lambdaTestAccessKey();

    @Key("application.type")
    @DefaultValue("extjs")
    String applicationType();
}
