package com.gsl.driver.provider;

import com.gsl.config.ConfigFactory;

/**
 * Docker Grid variant of SeleniumGridProvider.
 * Identical capability setup; only the hub URL differs (docker.grid.url config key).
 * Allows Docker Grid and plain Selenium Grid to coexist without code duplication.
 */
public final class DockerGridProvider extends SeleniumGridProvider {

    @Override
    protected String getGridUrl() {
        return ConfigFactory.getConfig().dockerGridUrl();
    }

    @Override
    public String getProviderName() {
        return "DockerGridProvider";
    }
}
