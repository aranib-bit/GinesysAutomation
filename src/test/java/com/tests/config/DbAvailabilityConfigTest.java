package com.tests.config;

import com.gsl.config.ConfigValidator;
import com.gsl.database.DbAvailability;
import com.gsl.enums.TestDataSourceMode;
import com.gsl.enums.TestEnvironment;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Pure checks for the db.available / testdata.source rules — no config files, browser or DB needed. */
public class DbAvailabilityConfigTest {

    @Test
    public void dbAvailableFlagParsesYesNoVariantsAndRejectsGarbage() {
        for (String yes : new String[]{"yes", "YES", " true ", "y", "1"}) assertTrue(DbAvailability.parseFlag(yes), yes);
        for (String no : new String[]{"no", "No", "false", "n", "0"}) assertFalse(DbAvailability.parseFlag(no), no);
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> DbAvailability.parseFlag("maybe"));
        assertTrue(e.getMessage().contains("maybe") && e.getMessage().contains("yes or no"), e.getMessage());
        expectThrows(IllegalArgumentException.class, () -> DbAvailability.parseFlag(null));
    }

    @Test
    public void noDbValidationModeDefaultsToApiAndAcceptsSkip() {
        assertEquals(DbAvailability.parseNoDbMode(null), DbAvailability.NoDbValidation.API);
        assertEquals(DbAvailability.parseNoDbMode("  "), DbAvailability.NoDbValidation.API);
        assertEquals(DbAvailability.parseNoDbMode("skip"), DbAvailability.NoDbValidation.SKIP);
        expectThrows(IllegalArgumentException.class, () -> DbAvailability.parseNoDbMode("later"));
    }

    @Test
    public void testDataSourceModeParsesAllThreeAndNamesValidChoices() {
        assertEquals(TestDataSourceMode.from("excel"), TestDataSourceMode.EXCEL);
        assertEquals(TestDataSourceMode.from(" DB "), TestDataSourceMode.DB);
        assertEquals(TestDataSourceMode.from("Api"), TestDataSourceMode.API);
        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> TestDataSourceMode.from("csv"));
        assertTrue(e.getMessage().contains("csv") && e.getMessage().contains("EXCEL")
                && e.getMessage().contains("DB") && e.getMessage().contains("API"), e.getMessage());
    }

    @Test
    public void validCombinationsProduceNoFatals() {
        assertTrue(ConfigValidator.checkDataSourceRules("DB", "yes", "API").isEmpty());
        assertTrue(ConfigValidator.checkDataSourceRules("EXCEL", "yes", "API").isEmpty());
        assertTrue(ConfigValidator.checkDataSourceRules("API", "yes", "SKIP").isEmpty());
        assertTrue(ConfigValidator.checkDataSourceRules("EXCEL", "no", "API").isEmpty());
        assertTrue(ConfigValidator.checkDataSourceRules("API", "no", "SKIP").isEmpty());
    }

    @Test
    public void dbSourceWithoutDbAvailableIsRejectedWithAClearMessage() {
        List<String> fatals = ConfigValidator.checkDataSourceRules("DB", "no", "API");
        assertEquals(fatals.size(), 1, fatals.toString());
        assertTrue(fatals.get(0).contains("testdata.source=DB") && fatals.get(0).contains("db.available=no"), fatals.get(0));
    }

    @Test
    public void invalidValuesAreReportedIndividually() {
        List<String> fatals = ConfigValidator.checkDataSourceRules("csv", "maybe", "later");
        assertEquals(fatals.size(), 3, fatals.toString());
    }

    @Test
    public void qaprodIsAKnownEnvironmentWithNoDbExpectations() {
        TestEnvironment env = TestEnvironment.from("qaprod", "test");
        assertEquals(env, TestEnvironment.QAPROD);
        assertEquals(env.folder(), "qaprod");
        assertEquals(env.expectedUiHost(), "qaprod.ginesys.cloud");
        assertEquals(env.expectedDbHost(), null);
        assertEquals(env.expectedDbName(), null);
        expectThrows(IllegalArgumentException.class, () -> TestEnvironment.from("prod-staging", "test"));
    }
}
