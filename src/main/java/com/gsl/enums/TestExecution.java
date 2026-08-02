package com.gsl.enums;

/**
 * The top-level test-execution context a multi-context test can switch between —
 * {@code ContextOrchestrator.switchTo()}'s only three inputs. Sits above (and is unrelated to)
 * {@link RunMode}, which is the execution *environment* (LOCAL/REMOTE/DOCKER_GRID/etc.), not
 * the test context.
 *
 * <ul>
 *   <li>{@link #WEB} — routes to DriverManager/PlatformType.WEB.</li>
 *   <li>{@link #MOBILE} — routes to DriverManager/PlatformType.ANDROID or .IOS. A bare
 *       {@code switchTo(MOBILE)} resolves the platform from config ({@code mobile.defaultPlatform});
 *       {@code switchTo(MOBILE, PlatformType.IOS)} overrides it explicitly.</li>
 *   <li>{@link #API} — routes to ApiSessionManager. Inherently ambiguous on its own (WEBAPI or
 *       PUBLICAPI?), so {@code switchTo(API, ApiPlatform)} always takes the sub-choice.</li>
 * </ul>
 */
public enum TestExecution {
    WEB, MOBILE, API
}
