package com.gsl.driver.provider;

import com.gsl.config.ConfigFactory;
import com.gsl.config.TargetOsResolver;
import com.gsl.enums.Browser;
import org.openqa.selenium.remote.AbstractDriverOptions;

/**
 * Docker Grid variant of SeleniumGridProvider.
 * Identical capability setup except the hub URL (docker.grid.url config key) and the requested
 * platform: the Docker Grid can host both Linux and Windows nodes, so each session asks for the
 * OS in {@code target.os}. Allows Docker Grid and plain Selenium Grid to coexist without code duplication.
 */
public final class DockerGridProvider extends SeleniumGridProvider {

    /** Docker Grid hub URL, read from {@code docker.grid.url} instead of the base class's {@code grid.url}. */
    @Override
    protected String getGridUrl() {
        return ConfigFactory.getBrowserConfig().dockerGridUrl();
    }

    /**
     * Adds {@code platformName} (from {@code target.os}, default linux) so the hub routes the session
     * to a node of that OS. Without it any node matches — fine with one Linux node, wrong once a
     * Windows node shares the hub. Not applied to the plain {@code REMOTE} grid, whose nodes' OS is unknown.
     */
    @Override
    protected AbstractDriverOptions<?> buildOptions(Browser browser) {
        AbstractDriverOptions<?> opts = super.buildOptions(browser);
        opts.setPlatformName(TargetOsResolver.current().platform().toString());
        return opts;
    }

    /** Reports {@code "DockerGridProvider"} for logs/reports instead of inheriting the base class's simple name. */
    @Override
    public String getProviderName() {
        return "DockerGridProvider";
    }
}
