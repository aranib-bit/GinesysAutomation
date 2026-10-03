package com.tests.config;

import com.gsl.config.ConfigFactory;
import com.gsl.config.ConfigValidator;
import com.gsl.config.EnvResolver;
import com.gsl.enums.TestEnvironment;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Environment-selection checks that need no browser or DB: run with {@code -Dtest=EnvConfigTest}
 * and any {@code -Denv=<name>} (default: whatever EnvResolver resolves, e.g. from env.local).
 */
public class EnvConfigTest {

    @Test
    public void resolvedEnvironmentDrivesConfigFolderAndExcelPath() {
        String env = EnvResolver.current();
        System.out.println("env=" + env + " source=" + EnvResolver.source()
                + " url=" + ConfigFactory.getConfig().url()
                + " db=" + ConfigFactory.getDataBaseConfig().databasename()
                + " webapi=" + ConfigFactory.getApiConfig().webApiBaseUri()
                + " apVoucher=" + ConfigFactory.getExcelDataConfig().apVoucherPath());
        assertEquals(ConfigFactory.getConfig().env(), env);
        assertEquals(ConfigFactory.getExcelDataConfig().apVoucherPath(), "env/" + env + "/data/Finance/ApVoucher.xlsx");
        assertEquals(ConfigFactory.getExcelDataConfig().usersListPath(), "env/" + env + "/data/UsersList.xlsx");
    }

    @Test
    public void everyWorkbookPathResolvesInTheActiveEnvironment() throws Exception {
        Object cfg = ConfigFactory.getExcelDataConfig();
        for (java.lang.reflect.Method m : com.gsl.config.ExcelDataConfig.class.getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                String path = (String) m.invoke(cfg);
                assertTrue(path.startsWith(EnvResolver.resourceFolder() + "/data/"), m.getName() + " -> " + path);
            }
        }
    }

    @Test
    public void unknownEnvironmentNamesTheValueAndEveryValidChoice() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> TestEnvironment.from("feature-99", "-Denv"));
        assertTrue(e.getMessage().contains("feature-99") && e.getMessage().contains("-Denv")
                && e.getMessage().contains("clone-staging") && e.getMessage().contains("feature-05"), e.getMessage());
    }

    @Test
    public void environmentValuesMatchTheEnumExpectations() {
        ConfigValidator.validate();
    }

    @Test
    public void unknownBrowserNamesTheValueAndEveryValidChoice() {
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
                () -> com.gsl.enums.Browser.from("opera", "-Dbrowser"));
        assertTrue(e.getMessage().contains("opera") && e.getMessage().contains("-Dbrowser")
                && e.getMessage().contains("chrome") && e.getMessage().contains("firefox")
                && e.getMessage().contains("edge") && e.getMessage().contains("safari"), e.getMessage());
    }

    @Test
    public void blankBrowserFallsBackToChromeAndKnownValuesAreCaseInsensitive() throws Exception {
        com.gsl.config.converter.StringToBrowserConverter c = new com.gsl.config.converter.StringToBrowserConverter();
        assertEquals(c.convert(null, ""), com.gsl.enums.Browser.CHROME);
        assertEquals(c.convert(null, null), com.gsl.enums.Browser.CHROME);
        assertEquals(c.convert(null, "FireFox"), com.gsl.enums.Browser.FIREFOX);
        assertEquals(c.convert(null, " edge "), com.gsl.enums.Browser.EDGE);
    }

    @Test
    public void reportBrowserIsTheBrowserThatRuns() {
        assertEquals(com.gsl.config.BrowserResolver.name(), com.gsl.config.BrowserResolver.current().name().toLowerCase());
    }

    @Test
    public void gridUrlCanBeOverriddenPerRun() {
        String override = System.getProperty("docker.grid.url");
        String actual = ConfigFactory.getBrowserConfig().dockerGridUrl();
        System.out.println("docker.grid.url -> " + actual);
        if (override != null && !override.isBlank()) {
            assertEquals(actual, override);
        }
    }
}
