package com.gsl.api.webapi.constants;

/** Protocol constants of the WebAPI login/session calls; deployment-specific values (login path, API prefix, retry counts) live in {@code ApiConfig}. */
public final class AuthDefaults {

    private AuthDefaults() {}

    /** OAuth2 password grant, the WebAPI {@code /token} login. */
    public static final String GRANT_TYPE_PASSWORD = "password";
    public static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    /** {@code error_description} code: the user is already logged in from another browser; {@code Session/KillUser} ends that session. */
    public static final String CODE_ALREADY_LOGGED_IN = "012";
    /** {@code error_description} code: the licence's user limit is reached, a transient capacity refusal. */
    public static final String CODE_LICENSE_EXCEEDED = "555555555";

    /** JWT claim carrying the numeric user id in the {@code ginesysauth} token. */
    public static final String NAME_IDENTIFIER_CLAIM = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier";

    /** The literal {@code sessionId} single-tenant logout sends (captured HAR). */
    public static final String NO_SESSION_ID = "null";
}
