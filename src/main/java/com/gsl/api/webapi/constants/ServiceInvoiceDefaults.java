package com.gsl.api.webapi.constants;

import java.util.List;

/** Stable constants of the Service Invoice Adhoc flow, taken from the captured UI traffic; nothing here depends on an environment. */
public final class ServiceInvoiceDefaults {

    private ServiceInvoiceDefaults() {}

    /** Menu short code of Service Invoice Adhoc in the scheme-document lookup. */
    public static final String MENU_SHORT_CODE = "ServInvAdhoc";
    /** Site type sent with every site-context lookup (owner site, owner OU, company). */
    public static final String SITE_TYPE = "OS-OO-CM";

    public static final List<String> SERVICE_QUERY_FIELDS =
            List.of("servicename", "hsN_SAC_CODE", "glname", "slname", "section", "tdsagency", "gsT_ITC_APPL_DIS");
    public static final String SERVICE_SORT = "[{\"property\":\"servicename\",\"direction\":\"ASC\"}]";
    public static final List<String> VENDOR_QUERY_FIELDS = List.of("name", "slid", "class", "generalLedger");
    public static final String VENDOR_SORT = "[{\"property\":\"name\",\"direction\":\"ASC\"}]";

    public static final int SERVICES_PAGE_SIZE = 50;
    public static final int VENDORS_PAGE_SIZE = 500;
    public static final int SCHEME_DOCS_PAGE_SIZE = 25;

    /** PURSRVMAIN.type for an ad-hoc service invoice (check constraint allows J, L, O, C, N). */
    public static final String DOCUMENT_TYPE = "N";
    /** {@code serviceType} of GetOtherDetails for an ad-hoc invoice. */
    public static final String OTHER_DETAILS_SERVICE_TYPE = "N";
    /** {@code status} of a new service line in the TDS calculation. */
    public static final String NEW_LINE_STATUS = "N";
    /** {@code invoiceCode} of ValidateReferenceNo for a new invoice. */
    public static final int NEW_INVOICE_CODE = 0;
    /** No excise form; the real responses never carry one for this flow. */
    public static final int FORM_CODE = 0;
    /** Longest document number the server accepts. */
    public static final int REFERENCE_NO_MAX_LENGTH = 16;
}
