package com.gsl.api.core;

import com.gsl.testdata.UserCredential;

/**
 * Pluggable authentication per {@code ApiPlatform} — WEBAPI (UserPool credential, two-token
 * login per captured HAR) and PUBLICAPI (OAuth2 client-credentials) need genuinely different
 * auth mechanics, so {@code ApiSessionFactory} selects the right strategy without {@link ApiClient}
 * itself ever needing to know which platform it's talking to.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link WebApiTokenAuthStrategy} — WEBAPI, backed by {@link WebApiLoginResult}</li>
 *   <li>{@link PublicApiOAuth2Strategy} — PUBLICAPI, OAuth2 client-credentials</li>
 * </ul>
 */
public interface ApiAuthStrategy {
    /**
     * Builds a fully-authenticated spec plus identifiers logout needs.
     *
     * @param credential the WEBAPI user to authenticate as; null for PUBLICAPI (no per-user credential)
     */
    AuthResult authenticate(String baseUri, UserCredential credential);
}
