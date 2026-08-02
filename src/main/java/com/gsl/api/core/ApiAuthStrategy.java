package com.gsl.api.core;

import com.gsl.testdata.UserCredential;

/**
 * Pluggable authentication per {@code ApiPlatform} — WEBAPI (UserPool credential, two-token
 * login per captured HAR) and PUBLICAPI (OAuth2 client-credentials) need genuinely different
 * auth mechanics, so {@code ApiSessionFactory} selects the right strategy without {@link ApiClient}
 * itself ever needing to know which platform it's talking to.
 */
public interface ApiAuthStrategy {
    /** Builds a fully-authenticated spec plus identifiers logout needs. credential is null for PUBLICAPI. */
    AuthResult authenticate(String baseUri, UserCredential credential);
}
