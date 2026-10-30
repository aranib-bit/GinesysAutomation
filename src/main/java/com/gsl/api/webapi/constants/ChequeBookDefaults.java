package com.gsl.api.webapi.constants;

/** Stable defaults of the Cheque Book create request, as captured in the CreateChequeBook HAR. Environment-dependent values live in {@code ApiConfig}. */
public final class ChequeBookDefaults {

    private ChequeBookDefaults() {}

    /** {@code code} of a book that does not exist yet. */
    public static final int NEW_BOOK_CODE = 0;
    public static final int INITIAL_DATA_VERSION = 0;
    /** {@code Type} of a normal (active) cheque leaf. */
    public static final String ACTIVE_CHEQUE_TYPE = "A";
    /** {@code Stat} of a leaf that has not been used. */
    public static final String UNUSED_CHEQUE_STATUS = "N";
    public static final String DEFAULT_REMARKS = "";
}
