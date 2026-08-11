package com.gsl.api.core;

import com.gsl.api.client.ArVoucherApiClient;
import com.gsl.api.client.DcApiClient;
import com.gsl.api.client.GrcApiClient;
import com.gsl.api.client.ItemApiClient;
import com.gsl.api.client.LedgerApiClient;
import com.gsl.api.client.ServiceInvoiceAdhocApiClient;
import com.gsl.api.client.SubLedgerOthersApiClient;
import com.gsl.api.client.TransferOutApiClient;
import com.gsl.enums.ApiPlatform;
import com.gsl.testdata.UserCredential;
import com.gsl.testdata.UserPool;
import io.restassured.specification.RequestSpecification;
import lombok.Builder;
import lombok.Getter;

/**
 * One authenticated session for one {@link ApiPlatform}, holding the five flow clients built
 * against its spec. Created by {@code ApiSessionFactory}, stored per-thread-per-platform by
 * {@code ApiSessionManager}.
 */
@Getter
@Builder
public final class ApiSessionBundle {
    private final ApiPlatform platform;
    private final RequestSpecification baseSpec;
    private final String userId;      // numeric internal id — needed for WEBAPI logout; null for PUBLICAPI
    private final String sessionId;   // nullable for single-tenant; populated for multi-tenant (see WebApiSessionClient)

    /** UserPool credential this session was built with — null for PUBLICAPI (no per-user credential). */
    private final UserCredential credential;
    /**
     * True when {@code ApiSessionFactory} self-acquired {@link #credential} from UserPool
     * (e.g. {@code createForWebApi()} called with no argument, as every {@code BackfillStrategy}
     * does) — in that case {@link #logout()} also releases it. False when the caller already
     * owns the credential's lifecycle (e.g. {@code APITest}/{@code MultiContextTest} acquired it
     * themselves and release it in their own tearDown) — releasing it here too would double-release.
     */
    private final boolean releaseCredentialOnLogout;

    private final ItemApiClient itemApiClient;
    private final GrcApiClient grcApiClient;
    private final DcApiClient dcApiClient;
    private final TransferOutApiClient transferOutApiClient;
    private final ServiceInvoiceAdhocApiClient serviceInvoiceAdhocApiClient;
    private final ArVoucherApiClient arVoucherApiClient;
    private final LedgerApiClient ledgerApiClient;
    private final SubLedgerOthersApiClient subLedgerOthersApiClient;

    /**
     * Terminates the server-side session (WEBAPI only — PUBLICAPI's OAuth2 client-credentials
     * token is app-level, no per-session logout call exists; K6BULK never holds a bundle at all)
     * and releases the UserPool credential back to its pool if this session self-acquired it.
     */
    public void logout() {
        if (platform == ApiPlatform.WEBAPI) {
            WebApiSessionClient.logout(this);
        }
        if (releaseCredentialOnLogout && credential != null) {
            UserPool.releaseSet1User(credential);
        }
    }
}
