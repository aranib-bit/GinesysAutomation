package com.gsl.api.webapi.constants;

/** Constants of the numbering-scheme admin screens, taken from the recorded UI request; nothing here depends on an environment. */
public final class DocSchemeDefaults {

    private DocSchemeDefaults() {}

    public static final int LIST_PAGE_SIZE = 25;
    public static final int STARTING_NUMBER = 1;
    public static final String PREFILL = "0";
    public static final String NO = "N";
    public static final String GENERATION_INTERVAL_YEARLY = "Y";
    public static final String GENERATION_BASIS_OU = "O";
    public static final String BASED_ON_SCHEME = "S";
    public static final String YEAR_SUFFIX = "/[FYY]";
    public static final String EMPTY = "";
    /** Companion of the sample number: width digits, the last one being the starting number. */
    public static final String SAMPLE_ZERO = "0";
}
