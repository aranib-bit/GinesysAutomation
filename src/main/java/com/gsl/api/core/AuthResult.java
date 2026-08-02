package com.gsl.api.core;

import io.restassured.specification.RequestSpecification;
import lombok.Builder;
import lombok.Getter;

/** Result of an {@link ApiAuthStrategy} — the authenticated spec plus whatever identifiers logout needs. */
@Getter
@Builder
public final class AuthResult {
    private final RequestSpecification spec;
    private final String userId;    // null for PUBLICAPI — OAuth2 client-credentials has no per-user id
    private final String sessionId; // null unless a multi-tenant WEBAPI login provided one
}
