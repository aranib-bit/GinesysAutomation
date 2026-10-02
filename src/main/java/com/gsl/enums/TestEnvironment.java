package com.gsl.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The closed set of target environments a run can be pointed at, selected with {@code -Denv=<folder>}
 * (see {@link com.gsl.config.EnvResolver}). {@link #folder()} is the name of the matching directory
 * under {@code src/test/resources/env/}; anything not listed here is rejected with an error that
 * names the offending value and every accepted one. Adding an environment means adding a constant
 * here and creating its folder.
 */
public enum TestEnvironment {
    STAGING("staging", "stage-ideal.ginesys.cloud", "172.22.4.4", "idealdb"),
    CLONE_STAGING("clone-staging", "clone-stage-ideal.ginesys.cloud", "172.22.4.4", "clone-idealdb"),
    FEATURE_01("feature-01", "qa-feature-01", "10.16.16.65", "FEATURE01"),
    FEATURE_02("feature-02", "qa-feature-02", "10.16.16.65", "FEATURE02"),
    FEATURE_03("feature-03", "qa-feature-03", "10.16.16.65", "FEATURE03"),
    FEATURE_04("feature-04", "qa-feature-04", "10.16.16.65", "FEATURE04"),
    FEATURE_05("feature-05", "qa-feature-05", "10.16.16.65", "FEATURE05"),
    FEATURE_06("feature-06", "qa-feature-06", "10.16.16.65", "FEATURE06"),
    FEATURE_07("feature-07", "qa-feature-07", "10.16.16.65", "FEATURE07"),
    FEATURE_08("feature-08", "qa-feature-08", "10.16.16.65", "FEATURE08"),
    MNT_01("mnt-01", null, "10.16.16.65", "MAINTENANCE01"),
    ORACLE_STAGING("oracle-staging", "qam-18", null, null),
    ORACLE_MNT("oracle-mnt", "erp-qa-mnt", "ERP-QA-MNT", "GINESYS"),
    PROD_STAGING("prod-staging", "qaprod.ginesys.cloud", null, null);

    private final String folder;
    private final String uiHost;
    private final String dbHost;
    private final String dbName;

    /** A {@code null} expected value means "not known yet" and is not checked. */
    TestEnvironment(String folder, String uiHost, String dbHost, String dbName) {
        this.folder = folder;
        this.uiHost = uiHost;
        this.dbHost = dbHost;
        this.dbName = dbName;
    }

    /** Host name the environment's UI URL (and WebAPI URL) must point at, or {@code null} if unchecked. */
    public String expectedUiHost() { return uiHost; }

    /** Database server the environment must use, or {@code null} if unchecked. */
    public String expectedDbHost() { return dbHost; }

    /** Database (or Oracle service) name the environment must use, or {@code null} if unchecked. */
    public String expectedDbName() { return dbName; }

    /** Folder name under {@code src/test/resources/env/}, also the value accepted by {@code -Denv}. */
    public String folder() {
        return folder;
    }

    /**
     * Maps a user-supplied value (folder name or constant name, case-insensitive) to its constant.
     *
     * @param value  the raw value, e.g. {@code feature-05} or {@code FEATURE_05}
     * @param source where the value came from ("-Denv", "env variable", "env.local" or "default"), used in the error
     * @throws IllegalArgumentException for an unknown value, listing the value, its source and all valid names
     */
    public static TestEnvironment from(String value, String source) {
        String wanted = value.trim();
        for (TestEnvironment e : values()) {
            if (e.folder.equalsIgnoreCase(wanted) || e.name().equalsIgnoreCase(wanted)) {
                return e;
            }
        }
        String valid = Arrays.stream(values()).map(TestEnvironment::folder).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown environment '" + value + "' (from " + source
                + "). Valid environments: " + valid + ".");
    }
}
