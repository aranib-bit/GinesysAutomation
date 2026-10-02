package com.gsl.enums;

/**
 * The Selenium/Appium execution environment a driver is created against — resolved from the
 * {@code runMode} config property (see {@code StringToRunModeConverter}, default {@link #LOCAL})
 * and dispatched by {@code ExecutionProviderRegistry}/{@code MasterDriverFactory} to the matching
 * {@code ExecutionProvider} (e.g. {@link #BROWSERSTACK} → {@code BrowserStackProvider}, {@link
 * #MOBILE} → {@code AppiumProvider}). Distinct from {@link TestExecution}, which is the test's
 * *context* (WEB/MOBILE/API) rather than *where* a WEB/MOBILE driver actually runs — see {@link
 * TestExecution}'s own Javadoc for that distinction.
 */
public enum RunMode {
    /** Driver runs on the local machine — the only mode {@link com.gsl.driver.ViewportEnforcer}
     *  doesn't force a fixed viewport size for. */
    LOCAL,
    /** Remote Selenium Grid (non-Docker hub). */
    REMOTE,
    /** Selenium Grid running inside Docker. */
    DOCKER_GRID,
    /** Appium-driven Android/iOS execution — platform resolved from {@code mobile.defaultPlatform}
     *  or an explicit override (see {@link TestExecution#MOBILE}). */
    MOBILE,
    /** BrowserStack cloud execution. */
    BROWSERSTACK,
    /** LambdaTest cloud execution. */
    LAMBDATEST
}
