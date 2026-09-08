package com.gsl.config;

import com.gsl.enums.ApiPlatform;
import org.aeonbits.owner.Config;

@Config.LoadPolicy(Config.LoadType.MERGE)
@Config.Sources({"system:properties", "file:${user.dir}/src/test/resources/config/api.properties"})
public interface ApiConfig extends Config {

    // ── WEBAPI ────────────────────────────────────────────────────────────────

    @Key("webapi.baseuri")
    String webApiBaseUri();

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

    @Key("webapi.auth.singleTenant.ginesysAuthHeaderName")
    @DefaultValue("ginesysauth")
    String singleTenantGinesysAuthHeaderName();

    @Key("webapi.auth.singleTenant.authorizationHeaderName")
    @DefaultValue("authorization")
    String singleTenantAuthorizationHeaderName();

    @Key("webapi.auth.multiTenant.ginesysAuthHeaderName")
    @DefaultValue("ginesysauth")
    String multiTenantGinesysAuthHeaderName();

    @Key("webapi.auth.multiTenant.authorizationHeaderName")
    @DefaultValue("authorization")
    String multiTenantAuthorizationHeaderName();

    /** Extra header multi-tenant logins may require beyond the two above (e.g. a tenant id) — blank if none. */
    @Key("webapi.auth.multiTenant.extraHeaderName")
    @DefaultValue("")
    String multiTenantExtraHeaderName();

    @Key("webapi.auth.multiTenant.extraHeaderValue")
    @DefaultValue("")
    String multiTenantExtraHeaderValue();

    // ── PUBLICAPI ─────────────────────────────────────────────────────────────

    @Key("publicapi.enabled")
    @DefaultValue("false")
    boolean publicApiEnabled();

    @Key("publicapi.baseuri")
    String publicApiBaseUri();

    @Key("publicapi.tokenUrl")
    String publicApiTokenUrl();

    @Key("publicapi.clientId")
    String publicApiClientId();

    @Key("publicapi.clientSecret")
    String publicApiClientSecret();

    // ── Data setup strategy (hybrid API + DB) ────────────────────────────────

    @Key("datasetup.${0}.strategy")
    @DefaultValue("HYBRID")
    String dataSetupStrategy(String flowKey);

    // ── Data-availability backfill (WEB -> no data -> WEBAPI/PUBLICAPI/k6 -> WEB) ──

    @Key("datafallback.smallQuantityThreshold")
    @DefaultValue("20")
    int smallQuantityThreshold();

    @Key("datafallback.smallQty.preferredOrder")
    @DefaultValue("WEBAPI,PUBLICAPI")
    ApiPlatform[] smallQtyPreferredOrder();

    @Key("mobile.defaultPlatform")
    @DefaultValue("ANDROID")
    com.gsl.enums.PlatformType mobileDefaultPlatform();

    @Key("mobile.appiumUrl")
    @DefaultValue("http://localhost:4723")
    String mobileAppiumUrl();

    @Key("mobile.deviceName")
    String mobileDeviceName();

    @Key("mobile.platformVersion")
    String mobilePlatformVersion();

    @Key("mobile.automationName")
    String mobileAutomationName();

    /** Path/package id of the app under test — the WMS app, once available. Blank until then. */
    @Key("mobile.app")
    @DefaultValue("")
    String mobileApp();

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

    @Key("datafallback.k6.binaryPath")
    @DefaultValue("k6")
    String k6BinaryPath();

    /** Resolves the k6 scenario script path for a given BackfillStrategy.k6ScriptKey(). */
    @Key("datafallback.k6.scriptPath.${0}")
    String k6ScriptPathFor(String scriptKey);

    // ── Tenant ────────────────────────────────────────────────────────────────

    @Key("api.tenant.active")
    @DefaultValue("stage-ideal")
    String activeTenant();

    @Key("api.tenant.multiTenantNames")
    @DefaultValue("qafeature,qahbros")
    String[] multiTenantNames();

    // ── Fixture-driven API endpoint testing (com.gsl.api.fixture) ───────────────
    // Real, valid live reference ids each flow's positive scenarios exercise against.

    @Key("fixture.serviceinvoiceadhoc.ownerSiteCode")
    String fixtureServiceInvoiceAdhocOwnerSiteCode();

    @Key("fixture.serviceinvoiceadhoc.vendorSlCode")
    String fixtureServiceInvoiceAdhocVendorSlCode();

    @Key("fixture.item.inventoryGroupCode")
    @DefaultValue("28")
    int fixtureItemInventoryGroupCode();

    @Key("fixture.grc.vendorCode")
    int fixtureGrcVendorCode();

    @Key("fixture.grc.siteCode")
    int fixtureGrcSiteCode();

    @Key("fixture.grc.ouCode")
    int fixtureGrcOuCode();

    @Key("fixture.grc.itemCode")
    String fixtureGrcItemCode();

    @Key("fixture.dc.itemCode")
    String fixtureDcItemCode();

    @Key("fixture.transferout.dcCode")
    String fixtureTransferOutDcCode();

    @Key("fixture.transferout.itemCode")
    String fixtureTransferOutItemCode();

    @Key("fixture.arvoucher.ownerSiteCode")
    int fixtureArVoucherOwnerSiteCode();

    @Key("fixture.arvoucher.ouCode")
    int fixtureArVoucherOuCode();

    @Key("fixture.arvoucher.empCode")
    long fixtureArVoucherEmpCode();

    // ! NOTE: deliberately NOT a `default` method here. Owner's dynamic proxy for @Config
    // interfaces does not correctly dispatch Java default methods — it routes the call through
    // its property-lookup InvocationHandler like any other interface method, gets null back
    // (no such property exists), and the proxy's primitive `boolean` return signature then NPEs
    // unboxing that null (confirmed: java.lang.Boolean.booleanValue() on a null invoke() result).
    // Use TenantUtils.isMultiTenant(config, tenant) instead — see com.gsl.config.TenantUtils.
}
