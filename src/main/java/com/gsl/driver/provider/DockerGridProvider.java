package com.gsl.driver.provider;

import com.gsl.config.ConfigFactory;

/**
 * Docker Grid variant of SeleniumGridProvider.
 * Identical capability setup; only the hub URL differs (docker.grid.url config key).
 * Allows Docker Grid and plain Selenium Grid to coexist without code duplication.
 */
public final class DockerGridProvider extends SeleniumGridProvider {

    /** Docker Grid hub URL, read from {@code docker.grid.url} instead of the base class's {@code grid.url}. */
    @Override
    protected String getGridUrl() {
        return ConfigFactory.getBrowserConfig().dockerGridUrl();
    }

    /** Reports {@code "DockerGridProvider"} for logs/reports instead of inheriting the base class's simple name. */
    @Override
    public String getProviderName() {
        return "DockerGridProvider";
    }
}
