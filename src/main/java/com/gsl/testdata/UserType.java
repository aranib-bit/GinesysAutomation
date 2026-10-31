package com.gsl.testdata;

/**
 * What a user account is used for. {@link UserPool} keeps one queue per type; a {@link UserCredential} carries a SET of types, but the only
 * multi-type combination allowed is {@code [UI, WEBAPI]} (see {@link UserCredentialLoader#parseTypes}): those two uses share one login, so the
 * pool hands such a user out to a browser test or to a WEBAPI session, never both at once.
 *
 * <ul>
 *   <li>{@code UI} - browser-driven Selenium tests.</li>
 *   <li>{@code WEBAPI} - RestAssured sessions against the WebAPI/Finance API (password login).</li>
 *   <li>{@code PUBLICAPI} - the account that owns public-API access (client-credentials; not a password login).</li>
 *   <li>{@code PERF} - load-test (k6) accounts; always alone, exported to k6 and never given to a Selenium test.</li>
 * </ul>
 */
public enum UserType {
    UI, WEBAPI, PUBLICAPI, PERF
}
