package com.gsl.config;

import com.gsl.enums.ApiPlatform;
import org.aeonbits.owner.Config;

/**
 * Configuration for every API surface the framework's API tests/backfills talk to — WEBAPI
 * (the app's own REST API), PUBLICAPI (OAuth2 client-credentials), mobile/Appium, the hybrid
 * API+DB data-setup strategy, the k6-based data-availability backfill, tenant selection, and
 * fixture reference ids used by {@code com.gsl.api.fixture} positive scenarios. Merges {@code
 * system:properties} ahead of {@code api.properties} so any key can be overridden per run with
 * {@code -Dkey=value} without editing the file.
 */
@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({
        "system:properties",
        "file:${user.dir}/src/test/resources/env/${env}/secrets.local.properties",
        "file:${user.dir}/src/test/resources/env/${env}/api.properties",
        "file:${user.dir}/src/test/resources/config/api.properties"
})
public interface ApiConfig extends Config {

    // ── WEBAPI ────────────────────────────────────────────────────────────────

    @Key("webapi.baseuri")
    String webApiBaseUri();

    // ── Cheque book backfill (Finance /cheques/create) ───────────────────────

    /** Leaves per auto-created cheque book. */
    @Key("backfill.chequebook.leafCount")
    @DefaultValue("25")
    int chequeBookLeafCount();

    /** First cheque number tried for an auto-created book; the range is moved up until {@code duplicatecheques} reports it free. */
    @Key("backfill.chequebook.startNumber")
    @DefaultValue("900000")
    int chequeBookStartNumber();

    /** Zero-padded cheque number width ({@code chequeWidth} in the Finance API). */
    @Key("backfill.chequebook.width")
    @DefaultValue("6")
    int chequeBookWidth();

    /** How many successive ranges are probed before giving up. */
    @Key("backfill.chequebook.maxRangeAttempts")
    @DefaultValue("20")
    int chequeBookMaxRangeAttempts();

    @Key("webapi.timeout.connect")
    @DefaultValue("10000")
    int webApiConnectTimeoutMs();

    @Key("webapi.timeout.read")
    @DefaultValue("15000")
    int webApiReadTimeoutMs();

    /**
     * Login endpoint, relative to {@link #webApiBaseUri()} — mirrors k6's lib/config.js
     * loginPath ("/WebAPI/token", tenant-agnostic across stage-ideal/qafeature/qahbros;
     * webApiBaseUri already bakes in "/WebAPI" so this is just "/token").
     */
    @Key("webapi.loginPath")
    @DefaultValue("/token")
    String webApiLoginPath();

    /**
     * How many times a login that the server refuses with "number of users allowed by your license has exceeded" (code 555555555) is
     * repeated, waiting {@link #webApiLicenseWaitSeconds()} between tries. That refusal is a transient capacity condition — seats free up
     * as other sessions end — not a bad credential, so waiting for a seat is the correct response; every wait is logged.
     */
    @Key("webapi.license.retries")
    @DefaultValue("12")
    int webApiLicenseRetries();

    @Key("webapi.license.waitSeconds")
    @DefaultValue("10")
    int webApiLicenseWaitSeconds();

    /**
     * Prefix every flow-client endpoint needs between {@link #webApiBaseUri()} and the endpoint
     * path itself — mirrors k6's lib/config.js apiPrefix ("/WebAPI/api", tenant-agnostic).
     * Confirmed via k6's flows/serviceInvoiceAdhoc.js building every call as
     * {@code {baseUrl}{apiPrefix}/...}; the flow clients here were missing this prefix entirely
     * (root cause of the systemic HTTP 404 on every flow-client endpoint).
     */
    @Key("webapi.apiPrefix")
    @DefaultValue("/api")
    String webApiApiPrefix();

    /** Resolves any webapi.endpoint.* key by its suffix, e.g. endpoint("item.create"). */
    @Key("webapi.endpoint.${0}")
    String endpoint(String key);

    /**
     * Base URI for the Finance module's own API (separate app path from {@link #webApiBaseUri()} —
     * confirmed via a captured HAR that the same authenticated WEBAPI session's ginesysauth
     * bearer header works unchanged against this path too, just a different base). Backs
     * {@code ArVoucherApiClient}.
     */
    @Key("webapi.financeApiBaseUri")
    String financeApiBaseUri();

    // ── WEBAPI auth header shape — differs per tenant mode, per k6's auth.js precedent ────
    // (k6 already has to special-case multi-tenant token *response* format — snake_case vs
    // camelCase — so the header *names* sent on every call are equally plausible to differ,
    // not hardcoded in Java; single-tenant's names are the ones confirmed via captured HAR.)

    /** Header name carrying the ginesysauth token for a single-tenant login. */
    @Key("webapi.auth.singleTenant.ginesysAuthHeaderName")
    @DefaultValue("ginesysauth")
    String singleTenantGinesysAuthHeaderName();

    /** Header name carrying the bearer/authorization token for a single-tenant login. */
    @Key("webapi.auth.singleTenant.authorizationHeaderName")
    @DefaultValue("authorization")
    String singleTenantAuthorizationHeaderName();

    /** Header name carrying the ginesysauth token for a multi-tenant login. */
    @Key("webapi.auth.multiTenant.ginesysAuthHeaderName")
    @DefaultValue("ginesysauth")
    String multiTenantGinesysAuthHeaderName();

    /** Header name carrying the bearer/authorization token for a multi-tenant login. */
    @Key("webapi.auth.multiTenant.authorizationHeaderName")
    @DefaultValue("authorization")
    String multiTenantAuthorizationHeaderName();

    /** Extra header multi-tenant logins may require beyond the two above (e.g. a tenant id) — blank if none. */
    @Key("webapi.auth.multiTenant.extraHeaderName")
    @DefaultValue("")
    String multiTenantExtraHeaderName();

    /** Extra header value paired with {@link #multiTenantExtraHeaderName()}. */
    @Key("webapi.auth.multiTenant.extraHeaderValue")
    @DefaultValue("")
    String multiTenantExtraHeaderValue();

    // ── PUBLICAPI ─────────────────────────────────────────────────────────────

    /** Master switch for PUBLICAPI (OAuth2 client-credentials) usage. */
    @Key("publicapi.enabled")
    @DefaultValue("false")
    boolean publicApiEnabled();

    /** Base URI of the PUBLICAPI. */
    @Key("publicapi.baseuri")
    String publicApiBaseUri();

    /** OAuth2 token endpoint URL for PUBLICAPI client-credentials login. */
    @Key("publicapi.tokenUrl")
    String publicApiTokenUrl();

    /** OAuth2 client id for PUBLICAPI. */
    @Key("publicapi.clientId")
    String publicApiClientId();

    /** OAuth2 client secret for PUBLICAPI. */
    @Key("publicapi.clientSecret")
    String publicApiClientSecret();

    /** Seconds before a PUBLICAPI token's expiry at which it is fetched again, so a request never races the expiry. */
    @Key("publicapi.token.refreshMarginSeconds")
    @DefaultValue("60")
    int publicApiTokenRefreshMarginSeconds();

    // ── Data setup strategy (hybrid API + DB) ────────────────────────────────

    /** Data-setup strategy (e.g. HYBRID) for the flow identified by {@code flowKey}. */
    @Key("datasetup.${0}.strategy")
    @DefaultValue("HYBRID")
    String dataSetupStrategy(String flowKey);

    // ── Data-availability backfill (WEB -> no data -> WEBAPI/PUBLICAPI/k6 -> WEB) ──

    /** Quantity below which the "small quantity" backfill path (see {@link #smallQtyPreferredOrder()}) applies. */
    @Key("datafallback.smallQuantityThreshold")
    @DefaultValue("20")
    int smallQuantityThreshold();

    /**
     * How the TDS of a document is rounded to a whole rupee before it is taken off the pending amount the Select Document popup offers:
     * {@code CEILING} (the TDS rounds up, so the popup's pending is lower), {@code FLOOR}, {@code HALF_UP} or {@code NONE}.
     */
    @Key("api.pending.tdsRoundoff")
    @DefaultValue("CEILING")
    String pendingTdsRoundoff();

    /** Rate (percent) of a TDS section (nature) the backfill creates when the environment has too few natures for a scenario. */
    @Key("backfill.tds.section.rate")
    @DefaultValue("1")
    double backfillTdsSectionRate();

    /** ISO date ({@code yyyy-MM-dd}) such a rate is valid from; empty means the first day of the current month. */
    @Key("backfill.tds.section.rateValidFrom")
    @DefaultValue("")
    String backfillTdsSectionRateValidFrom();

    /** The agency the backfill creates is booked to the first ledger of the TDS Agency form whose name contains this text (case-insensitive). */
    @Key("backfill.tds.agency.ledgerNameContains")
    @DefaultValue("TDS")
    String backfillTdsAgencyLedgerNameContains();

    /** Ordered platform preference for a small-quantity data-availability backfill. */
    @Key("datafallback.smallQty.preferredOrder")
    @DefaultValue("WEBAPI,PUBLICAPI")
    ApiPlatform[] smallQtyPreferredOrder();

    /** Ordered platform preference for creating a party master (vendor, customer, ...) as a backfill; a platform that cannot do it is skipped. */
    @Key("backfill.party.platformOrder")
    @DefaultValue("WEBAPI")
    ApiPlatform[] partyPlatformOrder();

    /** Default mobile platform (ANDROID/IOS) when a test doesn't specify one. */
    @Key("mobile.defaultPlatform")
    @DefaultValue("ANDROID")
    com.gsl.enums.PlatformType mobileDefaultPlatform();

    /** Appium server URL. */
    @Key("mobile.appiumUrl")
    @DefaultValue("http://localhost:4723")
    String mobileAppiumUrl();

    /** Target mobile device name for the Appium session. */
    @Key("mobile.deviceName")
    String mobileDeviceName();

    /** Target mobile platform version for the Appium session. */
    @Key("mobile.platformVersion")
    String mobilePlatformVersion();

    /** Appium automation engine name (e.g. UiAutomator2, XCUITest). */
    @Key("mobile.automationName")
    String mobileAutomationName();

    /** Path/package id of the app under test — the WMS app, once available. Blank until then. */
    @Key("mobile.app")
    @DefaultValue("")
    String mobileApp();

    /** iOS bundle id of the app under test. Blank until available. */
    @Key("mobile.bundleId")
    @DefaultValue("")
    String mobileBundleId();

    /** Inventory group code the AR-shortfall backfill's fresh items are created under. */
    @Key("datafallback.ar.defaultInventoryGroupCode")
    @DefaultValue("28")
    int defaultInventoryGroupCode();

    // ── Backfill pool-deadlock prevention ────────────────────────────────────
    // SessionPool holds one UI user per thread for a WEB test's whole lifetime; a backfill
    // firing on that same thread needs one MORE UI credential. These keys size and bound that.

    /** Spare UI-pool capacity beyond thread-count, reserved for backfill — validated at suite start. */
    @Key("datafallback.userPool.backfillHeadroom")
    @DefaultValue("1")
    int backfillUiPoolHeadroom();

    /**
     * WARN (log loudly, let the suite run) or FAIL (abort suite launch) when headroom is
     * insufficient. Deliberately no short backfill-specific acquire timeout: that would risk
     * aborting a legitimate backfill under transient contention right when the data actually
     * needs to be created — prevention happens here, at suite start, not by racing the clock
     * mid-test. Backfill's pool acquisition uses the framework's normal 600s default.
     */
    @Key("datafallback.userPool.headroomValidationMode")
    @DefaultValue("WARN")
    String backfillHeadroomValidationMode();

    /** Path/command used to invoke the k6 binary for a data-availability backfill scenario. */
    @Key("datafallback.k6.binaryPath")
    @DefaultValue("k6")
    String k6BinaryPath();

    /** Explicit override of the k6 scenario script path for a BackfillStrategy.k6ScriptKey(); blank = derived from {@link #k6Home()} by {@code K6Scripts}. */
    @Key("datafallback.k6.scriptPath.${0}")
    @DefaultValue("")
    String k6ScriptPathFor(String scriptKey);

    /** The k6 project directory, relative to the project directory (never an absolute D:\ path); holds flows/, lib/, scenarios/, config/. */
    @Key("k6.home")
    @DefaultValue("performance/k6")
    String k6Home();

    /**
     * k6 tenant (a {@code erp/config/environments/<tenant>.json} in the k6 project) the doc-scheme filler runs
     * against. Must point at the SAME host/DB the UI tests use — {@link #activeTenant()} often names a
     * different host (e.g. {@code clone-staging} keeps {@code stage-ideal}), so the filler has its own
     * setting. Blank falls back to {@link #activeTenant()}.
     */
    @Key("docscheme.fill.k6.tenant")
    @DefaultValue("")
    String docSchemeFillK6Tenant();

    /**
     * k6 tenant ({@code performance/k6/erp/config/environments/<tenant>.json}) every k6 launch of this environment runs against, and the name of
     * the generated users file. Must be the SAME host/DB the environment's UI tests use ({@link #activeTenant()} often names another host: clone-staging
     * keeps {@code stage-ideal}). Blank falls back to {@link #activeTenant()}; {@link #docSchemeFillK6Tenant()} still overrides it for the filler.
     */
    @Key("k6.tenant")
    @DefaultValue("")
    String k6Tenant();

    /**
     * Single-sign-on login URL used for users whose {@code logintype} is SSO (API mode and k6). Blank = no SSO configured; an SSO user then
     * fails fast with a clear message instead of falling back to the ERP login.
     */
    @Key("webapi.sso.loginUrl")
    @DefaultValue("")
    String ssoLoginUrl();

    /** Parallel k6 users (VUs) the filler uses; capped at the number of documents still needed. */
    @Key("docscheme.fill.vus")
    @DefaultValue("5")
    int docSchemeFillVus();

    /** k6 {@code MAX_DURATION} for one filler pass (k6 duration string, e.g. {@code 15m}). */
    @Key("docscheme.fill.maxDuration")
    @DefaultValue("15m")
    String docSchemeFillMaxDuration();

    /** Above this many documents the report shows per-VU summaries (plus every failure) instead of a line per document. */
    @Key("docscheme.fill.report.detailLimit")
    @DefaultValue("200")
    int docSchemeFillReportDetailLimit();

    // ── Tenant ────────────────────────────────────────────────────────────────

    /** Currently active tenant name (e.g. "stage-ideal"). */
    @Key("api.tenant.active")
    @DefaultValue("stage-ideal")
    String activeTenant();

    /** Comma-separated tenant names treated as multi-tenant — see {@link TenantUtils#isMultiTenant(ApiConfig, String)}. */
    @Key("api.tenant.multiTenantNames")
    @DefaultValue("qafeature,qahbros")
    String[] multiTenantNames();

    // ── Fixture-driven API endpoint testing (com.gsl.api.fixture) ───────────────
    // Real, valid live reference ids each flow's positive scenarios exercise against.

    /** Owner site code fixture for Service Invoice Adhoc positive scenarios. */
    @Key("fixture.serviceinvoiceadhoc.ownerSiteCode")
    String fixtureServiceInvoiceAdhocOwnerSiteCode();

    /** Vendor subledger code fixture for Service Invoice Adhoc positive scenarios. */
    @Key("fixture.serviceinvoiceadhoc.vendorSlCode")
    String fixtureServiceInvoiceAdhocVendorSlCode();

    /** Inventory group code fixture for item-endpoint positive scenarios. */
    @Key("fixture.item.inventoryGroupCode")
    @DefaultValue("28")
    int fixtureItemInventoryGroupCode();

    /** Vendor code fixture for GRC (Goods Receipt/Certificate) positive scenarios. */
    @Key("fixture.grc.vendorCode")
    int fixtureGrcVendorCode();

    /** Site code fixture for GRC positive scenarios. */
    @Key("fixture.grc.siteCode")
    int fixtureGrcSiteCode();

    /** Operating unit code fixture for GRC positive scenarios. */
    @Key("fixture.grc.ouCode")
    int fixtureGrcOuCode();

    /** Item code fixture for GRC positive scenarios. */
    @Key("fixture.grc.itemCode")
    String fixtureGrcItemCode();

    /** Item code fixture for DC (Distribution Center) positive scenarios. */
    @Key("fixture.dc.itemCode")
    String fixtureDcItemCode();

    /** DC code fixture for TransferOut positive scenarios. */
    @Key("fixture.transferout.dcCode")
    String fixtureTransferOutDcCode();

    /** Item code fixture for TransferOut positive scenarios. */
    @Key("fixture.transferout.itemCode")
    String fixtureTransferOutItemCode();

    /** Owner site code fixture for AR voucher positive scenarios. */
    @Key("fixture.arvoucher.ownerSiteCode")
    int fixtureArVoucherOwnerSiteCode();

    /** Operating unit code fixture for AR voucher positive scenarios. */
    @Key("fixture.arvoucher.ouCode")
    int fixtureArVoucherOuCode();

    /** Employee code fixture for AR voucher positive scenarios. */
    @Key("fixture.arvoucher.empCode")
    long fixtureArVoucherEmpCode();

    // ! NOTE: deliberately NOT a `default` method here. Owner's dynamic proxy for @Config
    // interfaces does not correctly dispatch Java default methods — it routes the call through
    // its property-lookup InvocationHandler like any other interface method, gets null back
    // (no such property exists), and the proxy's primitive `boolean` return signature then NPEs
    // unboxing that null (confirmed: java.lang.Boolean.booleanValue() on a null invoke() result).
    // Use TenantUtils.isMultiTenant(config, tenant) instead — see com.gsl.config.TenantUtils.
}
