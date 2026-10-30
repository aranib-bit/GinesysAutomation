export const TENANT = __ENV.TENANT || 'stage-ideal';

const env = JSON.parse(open(`../erp/config/environments/${TENANT}.json`));

export const baseUrl = env.baseUrl;
// USERS_FILE lets a scenario run against a dedicated credential pool (e.g. the
// 20-user billing-import pool) without touching the tenant's main user file.
export const usersFileName = __ENV.USERS_FILE || env.usersFile;
export const businessDataFileName = env.businessDataFile;
// "singleTenant" (each client has its own app instance, e.g. stage-ideal) vs
// "multiTenant" (multiple client orgs share one app instance/DB-routing
// layer). Descriptive metadata for tagging/reporting; loginPath/apiPrefix are
// the actual override points if a tenant's real API paths turn out to differ.
export const tenancyMode = env.tenancyMode || 'singleTenant';
export const loginPath = env.loginPath || '/WebAPI/token';
export const apiPrefix = env.apiPrefix || '/WebAPI/api';
// Separate API area from apiPrefix (confirmed via ERPUI/resources/data/settings.json's
// ginesysApiUrl field) - used by Connected-App/POS-item admin endpoints.
export const ginesysApiPrefix = env.ginesysApiPrefix || '/ginesysapi/api';
// UI path used to build the Referer header in lib/http.js - all tenants
// captured so far use /ERPUI/, but qaprod serves its UI from /ginesysweb/
// instead (confirmed live via its network traffic), so this is a per-tenant
// override point rather than a hardcoded constant.
export const uiPath = env.uiPath || '/ERPUI/';
// Some QA hosts use an internal/self-signed CA k6 doesn't trust by default
// (seen live on qafeature-erpapp: "certificate signed by unknown authority").
// Per-tenant opt-in rather than a global default so a real prod/stage host
// with a properly trusted cert doesn't silently skip verification.
export const insecureSkipTLSVerify = env.insecureSkipTLSVerify === true;
