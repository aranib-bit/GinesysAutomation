package com.gsl.api.core;

import com.gsl.api.client.ArVoucherApiClient;
import com.gsl.api.client.DcApiClient;
import com.gsl.api.client.GrcApiClient;
import com.gsl.api.client.ItemApiClient;
import com.gsl.api.client.ServiceInvoiceAdhocApiClient;
import com.gsl.api.client.TransferOutApiClient;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.ApiPlatform;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import com.gsl.testdata.UserType;
import io.restassured.specification.RequestSpecification;

/**
 * Builds an {@link ApiSessionBundle} for a given {@link ApiPlatform} — selects the right
 * {@link ApiAuthStrategy}, authenticates, and instantiates the five flow clients against the
 * resulting spec. {@code K6BULK} is never built here — it's a one-shot subprocess call, not a
 * session (see {@code K6ProcessBulkDataProvisioner}).
 */
public final class ApiSessionFactory {

    private ApiSessionFactory() {}

    /**
     * Acquires a fresh {@code UserType.UI} credential from UserPool and builds a WEBAPI session —
     * WEBAPI login is the same account a browser login would use, drawn from UsersList.xlsx's
     * UI users, not a separate API-type pool (confirmed: this environment has zero SET1 API
     * users configured, so acquiring UserType.API here always threw a SkipException). The
     * session releases this credential back to UserPool on logout() since it self-acquired it.
     *
     * <p>Uses the framework's normal pool-acquire timeout (same as every other caller —
     * {@code pool.acquire.timeout.seconds}, 600s default) rather than a short backfill-specific
     * one: a short timeout risks aborting a legitimate backfill under transient contention right
     * when the data actually needs to be created, which is worse than waiting. Deadlock
     * prevention is handled proactively instead, at suite start — see
     * {@code SuiteDataLoader.validateUserPoolAgainstThreadCount()}'s headroom check.
     */
    public static ApiSessionBundle createForWebApi() {
        UserCredential uiUser = UserPool.acquireSet1User(UserType.UI, "ApiSessionFactory.createForWebApi");
        return createForWebApi(uiUser, true);
    }

    /** Builds a WEBAPI session using an already-acquired credential (e.g. one APITest already holds). Caller retains release ownership. */
    public static ApiSessionBundle createForWebApi(UserCredential credential) {
        return createForWebApi(credential, false);
    }

    private static ApiSessionBundle createForWebApi(UserCredential credential, boolean releaseOnLogout) {
        // Flow-client endpoints (ItemMaster/Create, ServiceInvoice/GetOwnerSiteOtherDetails, ...)
        // live under {webApiBaseUri}{webApiApiPrefix} — confirmed against k6's own
        // flows/serviceInvoiceAdhoc.js + lib/config.js, which build every such call as
        // {baseUrl}{apiPrefix}/... Login itself is a separate, un-prefixed path (webApiLoginPath,
        // handled inside WebApiTokenAuthStrategy/WebApiSessionClient), so only the spec the flow
        // clients end up using needs the prefix baked in here.
        var cfg = ConfigFactory.getApiConfig();
        AuthResult auth = new WebApiTokenAuthStrategy()
                .authenticate(cfg.webApiBaseUri() + cfg.webApiApiPrefix(), credential);
        return build(ApiPlatform.WEBAPI, auth, credential, releaseOnLogout);
    }

    /** Builds a PUBLICAPI session — OAuth2 client-credentials, no UserPool involvement. */
    public static ApiSessionBundle createForPublicApi() {
        AuthResult auth = new PublicApiOAuth2Strategy()
                .authenticate(ConfigFactory.getApiConfig().publicApiBaseUri(), null);
        return build(ApiPlatform.PUBLICAPI, auth, null, false);
    }

    /** Dispatches to the right builder for any switchable platform (WEBAPI or PUBLICAPI; not K6BULK). */
    public static ApiSessionBundle create(ApiPlatform platform) {
        return switch (platform) {
            case WEBAPI -> createForWebApi();
            case PUBLICAPI -> createForPublicApi();
            case K6BULK -> throw new IllegalArgumentException(
                    "K6BULK is a one-shot backfill mechanism, not a session — never call ApiSessionFactory.create(K6BULK)");
        };
    }

    private static ApiSessionBundle build(ApiPlatform platform, AuthResult auth,
                                           UserCredential credential, boolean releaseOnLogout) {
        RequestSpecification spec = auth.getSpec();
        return ApiSessionBundle.builder()
                .platform(platform)
                .baseSpec(spec)
                .userId(auth.getUserId())
                .sessionId(auth.getSessionId())
                .credential(credential)
                .releaseCredentialOnLogout(releaseOnLogout)
                .itemApiClient(new ItemApiClient(spec))
                .grcApiClient(new GrcApiClient(spec))
                .dcApiClient(new DcApiClient(spec))
                .transferOutApiClient(new TransferOutApiClient(spec))
                .serviceInvoiceAdhocApiClient(new ServiceInvoiceAdhocApiClient(spec))
                .arVoucherApiClient(new ArVoucherApiClient(spec))
                .build();
    }
}
