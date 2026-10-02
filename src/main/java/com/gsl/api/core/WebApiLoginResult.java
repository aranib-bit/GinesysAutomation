package com.gsl.api.core;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of {@code WebApiSessionClient.login()} — both auth headers plus the identifiers
 * {@code WebApiSessionClient.logout()} needs to echo back.
 *
 * <p>Consumed by {@link WebApiTokenAuthStrategy#authenticate}, which copies these values into
 * the {@link AuthResult} it returns — this type is WEBAPI-internal (raw login response shape),
 * while {@link AuthResult} is the platform-agnostic result {@link ApiAuthStrategy} exposes.
 */
@Getter
@Builder
public final class WebApiLoginResult {
    private final String ginesysAuthToken; // JWT — Bearer value for the "ginesysauth" header
    private final String authorizationToken; // opaque token — Bearer value for the "authorization" header
    private final String userId;   // numeric internal id, parsed from the JWT's nameidentifier claim
    private final String sessionId; // nullable for single-tenant; populated for multi-tenant (TBD — see WebApiSessionClient)
}
