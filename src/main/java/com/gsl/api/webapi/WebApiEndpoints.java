package com.gsl.api.webapi;

import com.gsl.api.core.Endpoint;

/**
 * Every WebAPI / Finance API endpoint the framework calls, declared once and grouped by domain. A client never writes a path literal;
 * the base URI of each {@link com.gsl.api.core.Host} comes from {@code ApiConfig}. Domains are added here as their clients migrate to POJOs.
 */
public final class WebApiEndpoints {

    private WebApiEndpoints() {}

    /** Cheque Book master (Finance API). */
    public static final class Cheque {
        private Cheque() {}

        /** Which numbers of a range are already used for a bank GL (HTTP 200). */
        public static final Endpoint DUPLICATES = Endpoint.finance("/cheques/duplicatecheques");
        /** Creates a cheque book (HTTP 201). */
        public static final Endpoint CREATE = Endpoint.finance("/cheques/create");
    }

    /** Party masters (WebAPI): the Vendor Master family (Supplier, Jobber, Agent, Transporter, Channel, Staff) and Customer. */
    public static final class Party {
        private Party() {}

        public static final Endpoint VENDOR_CREATE = Endpoint.webApi("/VendorMaster/Create");
        public static final Endpoint VENDOR_UPDATE = Endpoint.webApi("/VendorMaster/Update");
        public static final Endpoint VENDOR_LIST = Endpoint.webApi("/VendorMaster/GetVendors");
        public static final Endpoint VENDOR_GET = Endpoint.webApi("/VendorMaster/GetVendor");
        public static final Endpoint OTHERS_CREATE = Endpoint.webApi("/EmployeeMaster/CreateFinanceSubLedgerOther");
        public static final Endpoint CUSTOMER_CREATE = Endpoint.webApi("/Customer/Create");
        public static final Endpoint CUSTOMER_LIST = Endpoint.webApi("/Customer/GetCustomers");
        public static final Endpoint CLASS_LOOKUP = Endpoint.webApi("/Shared/GetSubledgerClassLookup");
        public static final Endpoint CITY_LOOKUP = Endpoint.webApi("/Shared/GetCityLookup");
    }

    /** Finance voucher lookups and the per-module voucher endpoints (AP, AR, General Voucher). */
    public static final class Voucher {
        private Voucher() {}

        public static final Endpoint DOC_NUMBER_SCHEMES = Endpoint.finance("/docnumberschemes");
        public static final Endpoint CASH_BANK_LEDGERS = Endpoint.finance("/ledgers/cashbankledgers");
        public static final Endpoint LEDGERS = Endpoint.finance("/ledgers");
        public static final Endpoint SUB_LEDGERS = Endpoint.finance("/subledgers");
        public static final Endpoint SITES = Endpoint.finance("/sites");
        public static final Endpoint TDS_SECTION_NAME = Endpoint.finance("/tdssection/tdsname");
        public static final Endpoint CHEQUES = Endpoint.finance("/cheques");

        /** {@code /vouchers/<segment>} (create, edit, delete): segment is {@code ap}, {@code ar} or {@code general}. */
        public static Endpoint root(String segment) {
            return Endpoint.finance("/vouchers/" + segment);
        }

        /** {@code /vouchers/<segment>/<action>} ({@code revert}, {@code release}). */
        public static Endpoint action(String segment, String action) {
            return Endpoint.finance("/vouchers/" + segment + "/" + action);
        }

        /** {@code /vouchers/<segment>/validations/voucherdate}. */
        public static Endpoint validateVoucherDate(String segment) {
            return Endpoint.finance("/vouchers/" + segment + "/validations/voucherdate");
        }

        /** {@code /vouchers/<rootviewSegment>/rootview}: segment is {@code apvouchers}, {@code arvouchers} or {@code generalvouchers}. */
        public static Endpoint rootview(String rootviewSegment) {
            return Endpoint.finance("/vouchers/" + rootviewSegment + "/rootview");
        }
    }

    /**
     * The per-module voucher lifecycle calls as constants (the {@link Voucher} methods take a segment, which a registry export cannot list):
     * create (POST), revert (PUT), release (PUT) and delete (DELETE) of AP, AR and General vouchers.
     */
    public static final class Lifecycle {
        private Lifecycle() {}

        public static final Endpoint AP_CREATE = Voucher.root("ap");
        public static final Endpoint AR_CREATE = Voucher.root("ar");
        public static final Endpoint GV_CREATE = Voucher.root("general");
        public static final Endpoint AP_REVERT = Voucher.action("ap", "revert");
        public static final Endpoint AP_RELEASE = Voucher.action("ap", "release");
        public static final Endpoint AP_DELETE = Voucher.root("ap");
        public static final Endpoint AR_REVERT = Voucher.action("ar", "revert");
        public static final Endpoint AR_RELEASE = Voucher.action("ar", "release");
        public static final Endpoint AR_DELETE = Voucher.root("ar");
        public static final Endpoint GV_REVERT = Voucher.action("general", "revert");
        public static final Endpoint GV_RELEASE = Voucher.action("general", "release");
        public static final Endpoint GV_DELETE = Voucher.root("general");
    }

    /** Document adjustment (tagging) endpoints (Finance API). */
    public static final class DocumentAdjustment {
        private DocumentAdjustment() {}

        /** Removes every tag of a base document ({@code AdjustedDocuments} empty). */
        public static final Endpoint UNTAG = Endpoint.finance("/documentAdjustments");

        /** The per-module reads as constants (the methods below take a segment, which a registry export cannot list). */
        public static final Endpoint AP_PENDING = Endpoint.finance("/DocumentAdjustments/apvoucher");
        public static final Endpoint AR_PENDING = Endpoint.finance("/DocumentAdjustments/arvoucher");
        public static final Endpoint AP_REFERENCE = Endpoint.finance("/DocumentAdjustments/ap/reference");
        public static final Endpoint AR_REFERENCE = Endpoint.finance("/DocumentAdjustments/ar/reference");

        /** {@code /DocumentAdjustments/<module>}: documents with a pending balance ({@code apvoucher} or {@code arvoucher}). */
        public static Endpoint pending(String module) {
            return Endpoint.finance("/DocumentAdjustments/" + module);
        }

        /** {@code /DocumentAdjustments/<ap|ar>/reference}: pending amount per reference site. */
        public static Endpoint reference(String segment) {
            return Endpoint.finance("/DocumentAdjustments/" + segment + "/reference");
        }
    }

    /** User and session endpoints of the WebAPI. */
    public static final class User {
        private User() {}

        /** The user's assigned sites, OU and profile flags (body {@code {}}, query {@code userId=0}). */
        public static final Endpoint ALL_SITES = Endpoint.webApi("/UserRolePolicy/GetAllSiteForAUser");
    }

    /** Service Invoice Adhoc (WebAPI): the lookups the UI calls before Save, then Save itself. */
    public static final class ServiceInvoice {
        private ServiceInvoice() {}

        public static final Endpoint OWNER_SITE_DETAILS = Endpoint.webApi("/ServiceInvoice/GetOwnerSiteOtherDetails");
        public static final Endpoint SERVICES = Endpoint.webApi("/ServiceInvoice/GetServices");
        public static final Endpoint SCHEME_DOC_LIST = Endpoint.webApi("/RT/GetAdmSchemeDocList");
        public static final Endpoint VENDORS = Endpoint.webApi("/ServiceInvoice/GetVendors");
        public static final Endpoint VALIDATE_REFERENCE_NO = Endpoint.webApi("/ServiceInvoice/ValidateReferenceNo");
        public static final Endpoint OTHER_DETAILS = Endpoint.webApi("/ServiceInvoice/GetOtherDetails");
        public static final Endpoint LEDGER_TAGGED = Endpoint.webApi("/ServiceInvoice/IsLedgerTaggedInFINGL_SITE");
        public static final Endpoint CALCULATE_CHARGES = Endpoint.webApi("/ServiceInvoice/CalculateServiceCharges");
        public static final Endpoint CALCULATE_TDS = Endpoint.webApi("/ServiceInvoice/GetCalculatedTdsRateAndAmount");
        public static final Endpoint SAVE = Endpoint.webApi("/ServiceInvoice/Save");
    }

    /** Goods Receipt Challan (WebAPI). */
    public static final class Grc {
        private Grc() {}

        public static final Endpoint VENDOR_TAX_REGION = Endpoint.webApi("/GRC/GetGRCVendorTaxRegion");
        public static final Endpoint SAVE = Endpoint.webApi("/GRC/Save");
    }

    /** Delivery Challan (WebAPI). */
    public static final class Dc {
        private Dc() {}

        public static final Endpoint PACKET_BARCODE = Endpoint.webApi("/DC/GetPacketBarcode");
        public static final Endpoint SAVE = Endpoint.webApi("/DC/Save");
    }

    /** Transfer Out is a Sales Invoice (SI) against a DC (WebAPI). */
    public static final class TransferOut {
        private TransferOut() {}

        public static final Endpoint SEARCH_CHALLAN = Endpoint.webApi("/SI/SearchChallan");
        public static final Endpoint SAVE = Endpoint.webApi("/SI/Save");
    }

    /** Audit log reads (WebAPI). */
    public static final class Audit {
        private Audit() {}

        public static final Endpoint APPLICABLE_MODULES = Endpoint.webApi("/AuditLog/GetApplicableAuditModules");
        public static final Endpoint MODULE_LOG = Endpoint.webApi("/AuditLog/GetAuditModuleLog");
        public static final Endpoint HISTORY = Endpoint.webApi("/AuditLog/GetTabWiseAuditTransactonDetails");
    }

    /** WebAPI session calls made around login; the login path itself ({@code webapi.loginPath}) and the API prefix are configuration. */
    public static final class Auth {
        private Auth() {}

        public static final Endpoint KILL_USER = Endpoint.webApi("/Session/KillUser");
        public static final Endpoint LOGOUT = Endpoint.webApi("/Session/Logout");
    }

    /** Item master (WebAPI). */
    public static final class Item {
        private Item() {}

        public static final Endpoint UDF_CONFIG_LIST = Endpoint.webApi("/udf/GetUDFConfigList");
        public static final Endpoint GST_HSN_LOOKUP = Endpoint.webApi("/Shared/GetGSTHSNLookup");
        public static final Endpoint GROUP_DEFAULT_VALUES = Endpoint.webApi("/ItemMaster/GetGroupDefaultValues");
        public static final Endpoint UNIT_OF_MEASUREMENT = Endpoint.webApi("/ItemMaster/GetUnitOfMeasurement");
        public static final Endpoint CREATE = Endpoint.webApi("/ItemMaster/Create");
    }

    /** Ledger master (WebAPI). */
    public static final class Ledger {
        private Ledger() {}

        public static final Endpoint ACCOUNT_GROUP_TREE = Endpoint.webApi("/FinGroup/GetAccountGrpTree");
        public static final Endpoint SITES_FOR_FINANCE_LOOKUP = Endpoint.webApi("/Shared/GetSitesForFinanceLookup");
        public static final Endpoint CREATE = Endpoint.webApi("/Ledger/Create");
    }

    /** Shared lookups the party masters use (WebAPI). */
    public static final class Lookup {
        private Lookup() {}

        public static final Endpoint TDS_NAME = Endpoint.webApi("/Shared/GetTDSNameLookup");
        public static final Endpoint TDS_SOURCE = Endpoint.webApi("/Shared/GetTDSSourceLookup");
        public static final Endpoint ORGANIZATION_UNIT = Endpoint.webApi("/Shared/GetOrganizationUnitLookup");
    }

    /** Numbering-scheme and site-tagging admin screens (WebAPI). */
    public static final class DocScheme {
        private DocScheme() {}

        public static final Endpoint LIST = Endpoint.webApi("/MenuDocScheme/GetListOfDocScheme");
        public static final Endpoint ACCOUNTING_PERIOD = Endpoint.webApi("/MenuDocScheme/AccountingPeriodInfo");
        public static final Endpoint CREATE = Endpoint.webApi("/MenuDocScheme/Create");
        public static final Endpoint SAVE_MENU_MAP = Endpoint.webApi("/MenuDocScheme/SaveDocMenuMap");
        public static final Endpoint UNTAGGED_FOR_SITE = Endpoint.webApi("/AdmOrganizationSite/GetUnTaggedDocumentList");
        public static final Endpoint SITE_GET = Endpoint.webApi("/AdmOrganizationSite/GetOrganizationSite");
        public static final Endpoint SITE_UPDATE = Endpoint.webApi("/AdmOrganizationSite/Update");
    }

    /** TDS masters (Finance API): source, agency, section (nature), and the agency form's lookups. */
    public static final class Tds {
        private Tds() {}

        public static final Endpoint SOURCE_LIST = Endpoint.finance("/tdssource/all");
        public static final Endpoint SOURCE_CREATE = Endpoint.finance("/tdssource");
        public static final Endpoint AGENCY_LIST = Endpoint.finance("/TDSAgency/list");
        public static final Endpoint AGENCY_CREATE = Endpoint.finance("/tdsagency");
        public static final Endpoint SECTION_LIST = Endpoint.finance("/tdssection");
        public static final Endpoint SECTION_CREATE = Endpoint.finance("/tdssection/tdssection");
        public static final Endpoint UDF_CONFIG = Endpoint.finance("/udfconfigurations");
        /** The Account Class master (WebAPI); the TDS Agency class is created here. */
        public static final Endpoint ACCOUNT_CLASS_CREATE = Endpoint.webApi("/AccountClass/Create");
    }
}
