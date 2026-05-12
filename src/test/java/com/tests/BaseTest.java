package com.tests;

import com.gsl.config.ConfigFactory;
import com.gsl.config.FrameworkConfig;

/**
 * Abstract root for all test types — WebTest, APITest, MobileTest.
 *
 * <p>Holds the single shared {@link FrameworkConfig} reference so every subclass
 * can read {@code config.properties} values without repeating the factory call.</p>
 *
 * <p>Lifecycle hooks ({@code @Before*} / {@code @After*}) are defined by the
 * platform-specific subclasses, not here:</p>
 * <ul>
 *   <li>{@link WebTest}    — Selenium browser + UserPool (SET-1 UI user)</li>
 *   <li>{@link APITest}    — RestAssured + UserPool (SET-1 API user)</li>
 *   <li>{@link MobileTest} — Appium driver + UserPool (SET-1 UI user)</li>
 * </ul>
 *
 * <p>Concrete test classes should extend the platform type they need
 * ({@code WebTest}, {@code APITest}, or {@code MobileTest}) — not this class directly.</p>
 */
public abstract class BaseTest {

    protected final FrameworkConfig config = ConfigFactory.getConfig();
}
