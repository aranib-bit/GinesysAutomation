package com.gsl.api.webapi.constants;

/** Constants of the TDS master screens (source, agency, section), from the recorded UI requests; nothing here depends on an environment. */
public final class TdsMasterDefaults {

    private TdsMasterDefaults() {}

    public static final int LIST_PAGE_SIZE = 50;
    public static final String SOURCE_SORT = "tdsSource ASC";
    public static final String AGENCY_SORT = "name ASC";
    public static final String SECTION_SORT = "tdsName ASC";

    /** {@code sourceType} of a corporate TDS source. */
    public static final String SOURCE_TYPE_CORPORATE = "C";
    public static final String NOT_EXTINCT = "N";
    /** What the lists report for an active record. */
    public static final String LIST_NOT_EXTINCT = "No";
    /** The agency class type the TDS Agency screen offers ({@code SubLedgers?module=tdsagency} returns it as {@code classType}). */
    public static final String AGENCY_CLASS_TYPE = "D";
    public static final String AGENCY_MODULE = "tdsagency";
    public static final String AGENCY_UDF_MODULE = "financetdsagency";
    public static final java.util.List<String> AGENCY_CLASS_QUERY_FIELDS = java.util.List.of("className", "classTypeDisplay");
    public static final java.util.List<String> AGENCY_LEDGER_QUERY_FIELDS = java.util.List.of("glName", "typeDisplay", "sourceTypeDisplay");
    public static final int LOOKUP_PAGE_SIZE = 25;
    /** {@code type} of a liability ledger in the ledger lookups. */
    public static final String LEDGER_TYPE_LIABILITY = "L";

    /** {@code cmpcode} of the TDS Agency class in the recorded request: the class is not company-specific. */
    public static final int ACCOUNT_CLASS_COMPANY_CODE = 0;
    /** The Account Class screen's ExtJS model id, sent with every save. */
    public static final String ACCOUNT_CLASS_MODEL_ID = "App.view.finance.setup.ChartOfAccounts.AccountClass.model.AdmClsEntity-1";

    public static final String SECTION_TYPE_SERVICES = "Services";
    public static final String SECTION_TYPE_GOODS = "Goods";

    /** Prefix of every throw-away master this framework creates, so test data is recognisable and never mistaken for real masters. */
    public static final String NAME_PREFIX = "ZZ API TDS";
    public static final long VISIBLE_TIMEOUT_MS = 30_000L;
    public static final long VISIBLE_POLL_MS = 1_500L;
}
