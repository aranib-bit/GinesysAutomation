// GENERATED from com.gsl.api.webapi.WebApiEndpoints by K6ContractTest - do not edit; regenerate with -Dk6.contract.write=true
// host WEB_API -> apiPrefix (/WebAPI/api), FINANCE_API -> financeApiPrefix (/Finance/api); see lib/api.js
export const Tds = {
  SOURCE_LIST: { host: 'FINANCE_API', path: '/tdssource/all' },
  SOURCE_CREATE: { host: 'FINANCE_API', path: '/tdssource' },
  AGENCY_LIST: { host: 'FINANCE_API', path: '/TDSAgency/list' },
  AGENCY_CREATE: { host: 'FINANCE_API', path: '/tdsagency' },
  SECTION_LIST: { host: 'FINANCE_API', path: '/tdssection' },
  SECTION_CREATE: { host: 'FINANCE_API', path: '/tdssection/tdssection' },
  UDF_CONFIG: { host: 'FINANCE_API', path: '/udfconfigurations' },
  ACCOUNT_CLASS_CREATE: { host: 'WEB_API', path: '/AccountClass/Create' },
};
export const DocScheme = {
  LIST: { host: 'WEB_API', path: '/MenuDocScheme/GetListOfDocScheme' },
  ACCOUNTING_PERIOD: { host: 'WEB_API', path: '/MenuDocScheme/AccountingPeriodInfo' },
  CREATE: { host: 'WEB_API', path: '/MenuDocScheme/Create' },
  SAVE_MENU_MAP: { host: 'WEB_API', path: '/MenuDocScheme/SaveDocMenuMap' },
  UNTAGGED_FOR_SITE: { host: 'WEB_API', path: '/AdmOrganizationSite/GetUnTaggedDocumentList' },
  SITE_GET: { host: 'WEB_API', path: '/AdmOrganizationSite/GetOrganizationSite' },
  SITE_UPDATE: { host: 'WEB_API', path: '/AdmOrganizationSite/Update' },
};
export const Lookup = {
  TDS_NAME: { host: 'WEB_API', path: '/Shared/GetTDSNameLookup' },
  TDS_SOURCE: { host: 'WEB_API', path: '/Shared/GetTDSSourceLookup' },
  ORGANIZATION_UNIT: { host: 'WEB_API', path: '/Shared/GetOrganizationUnitLookup' },
};
export const Ledger = {
  ACCOUNT_GROUP_TREE: { host: 'WEB_API', path: '/FinGroup/GetAccountGrpTree' },
  SITES_FOR_FINANCE_LOOKUP: { host: 'WEB_API', path: '/Shared/GetSitesForFinanceLookup' },
  CREATE: { host: 'WEB_API', path: '/Ledger/Create' },
};
export const Item = {
  UDF_CONFIG_LIST: { host: 'WEB_API', path: '/udf/GetUDFConfigList' },
  GST_HSN_LOOKUP: { host: 'WEB_API', path: '/Shared/GetGSTHSNLookup' },
  GROUP_DEFAULT_VALUES: { host: 'WEB_API', path: '/ItemMaster/GetGroupDefaultValues' },
  UNIT_OF_MEASUREMENT: { host: 'WEB_API', path: '/ItemMaster/GetUnitOfMeasurement' },
  CREATE: { host: 'WEB_API', path: '/ItemMaster/Create' },
};
export const Auth = {
  KILL_USER: { host: 'WEB_API', path: '/Session/KillUser' },
  LOGOUT: { host: 'WEB_API', path: '/Session/Logout' },
};
export const Audit = {
  APPLICABLE_MODULES: { host: 'WEB_API', path: '/AuditLog/GetApplicableAuditModules' },
  MODULE_LOG: { host: 'WEB_API', path: '/AuditLog/GetAuditModuleLog' },
  HISTORY: { host: 'WEB_API', path: '/AuditLog/GetTabWiseAuditTransactonDetails' },
};
export const TransferOut = {
  SEARCH_CHALLAN: { host: 'WEB_API', path: '/SI/SearchChallan' },
  SAVE: { host: 'WEB_API', path: '/SI/Save' },
};
export const Dc = {
  PACKET_BARCODE: { host: 'WEB_API', path: '/DC/GetPacketBarcode' },
  SAVE: { host: 'WEB_API', path: '/DC/Save' },
};
export const Grc = {
  VENDOR_TAX_REGION: { host: 'WEB_API', path: '/GRC/GetGRCVendorTaxRegion' },
  SAVE: { host: 'WEB_API', path: '/GRC/Save' },
};
export const ServiceInvoice = {
  OWNER_SITE_DETAILS: { host: 'WEB_API', path: '/ServiceInvoice/GetOwnerSiteOtherDetails' },
  SERVICES: { host: 'WEB_API', path: '/ServiceInvoice/GetServices' },
  SCHEME_DOC_LIST: { host: 'WEB_API', path: '/RT/GetAdmSchemeDocList' },
  VENDORS: { host: 'WEB_API', path: '/ServiceInvoice/GetVendors' },
  VALIDATE_REFERENCE_NO: { host: 'WEB_API', path: '/ServiceInvoice/ValidateReferenceNo' },
  OTHER_DETAILS: { host: 'WEB_API', path: '/ServiceInvoice/GetOtherDetails' },
  LEDGER_TAGGED: { host: 'WEB_API', path: '/ServiceInvoice/IsLedgerTaggedInFINGL_SITE' },
  CALCULATE_CHARGES: { host: 'WEB_API', path: '/ServiceInvoice/CalculateServiceCharges' },
  CALCULATE_TDS: { host: 'WEB_API', path: '/ServiceInvoice/GetCalculatedTdsRateAndAmount' },
  SAVE: { host: 'WEB_API', path: '/ServiceInvoice/Save' },
};
export const User = {
  ALL_SITES: { host: 'WEB_API', path: '/UserRolePolicy/GetAllSiteForAUser' },
};
export const DocumentAdjustment = {
  UNTAG: { host: 'FINANCE_API', path: '/documentAdjustments' },
  AP_PENDING: { host: 'FINANCE_API', path: '/DocumentAdjustments/apvoucher' },
  AR_PENDING: { host: 'FINANCE_API', path: '/DocumentAdjustments/arvoucher' },
  AP_REFERENCE: { host: 'FINANCE_API', path: '/DocumentAdjustments/ap/reference' },
  AR_REFERENCE: { host: 'FINANCE_API', path: '/DocumentAdjustments/ar/reference' },
};
export const Lifecycle = {
  AP_CREATE: { host: 'FINANCE_API', path: '/vouchers/ap' },
  AR_CREATE: { host: 'FINANCE_API', path: '/vouchers/ar' },
  GV_CREATE: { host: 'FINANCE_API', path: '/vouchers/general' },
  AP_REVERT: { host: 'FINANCE_API', path: '/vouchers/ap/revert' },
  AP_RELEASE: { host: 'FINANCE_API', path: '/vouchers/ap/release' },
  AP_DELETE: { host: 'FINANCE_API', path: '/vouchers/ap' },
  AR_REVERT: { host: 'FINANCE_API', path: '/vouchers/ar/revert' },
  AR_RELEASE: { host: 'FINANCE_API', path: '/vouchers/ar/release' },
  AR_DELETE: { host: 'FINANCE_API', path: '/vouchers/ar' },
  GV_REVERT: { host: 'FINANCE_API', path: '/vouchers/general/revert' },
  GV_RELEASE: { host: 'FINANCE_API', path: '/vouchers/general/release' },
  GV_DELETE: { host: 'FINANCE_API', path: '/vouchers/general' },
};
export const Voucher = {
  DOC_NUMBER_SCHEMES: { host: 'FINANCE_API', path: '/docnumberschemes' },
  CASH_BANK_LEDGERS: { host: 'FINANCE_API', path: '/ledgers/cashbankledgers' },
  LEDGERS: { host: 'FINANCE_API', path: '/ledgers' },
  SUB_LEDGERS: { host: 'FINANCE_API', path: '/subledgers' },
  SITES: { host: 'FINANCE_API', path: '/sites' },
  TDS_SECTION_NAME: { host: 'FINANCE_API', path: '/tdssection/tdsname' },
  CHEQUES: { host: 'FINANCE_API', path: '/cheques' },
};
export const Party = {
  VENDOR_CREATE: { host: 'WEB_API', path: '/VendorMaster/Create' },
  VENDOR_UPDATE: { host: 'WEB_API', path: '/VendorMaster/Update' },
  VENDOR_LIST: { host: 'WEB_API', path: '/VendorMaster/GetVendors' },
  VENDOR_GET: { host: 'WEB_API', path: '/VendorMaster/GetVendor' },
  OTHERS_CREATE: { host: 'WEB_API', path: '/EmployeeMaster/CreateFinanceSubLedgerOther' },
  CUSTOMER_CREATE: { host: 'WEB_API', path: '/Customer/Create' },
  CUSTOMER_LIST: { host: 'WEB_API', path: '/Customer/GetCustomers' },
  CLASS_LOOKUP: { host: 'WEB_API', path: '/Shared/GetSubledgerClassLookup' },
  CITY_LOOKUP: { host: 'WEB_API', path: '/Shared/GetCityLookup' },
};
export const Cheque = {
  DUPLICATES: { host: 'FINANCE_API', path: '/cheques/duplicatecheques' },
  CREATE: { host: 'FINANCE_API', path: '/cheques/create' },
};
