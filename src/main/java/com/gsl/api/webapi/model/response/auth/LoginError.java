package com.gsl.api.webapi.model.response.auth;

import com.gsl.api.webapi.constants.AuthDefaults;

/** A login refusal as the server encodes it in {@code error_description}: {@code <code>|<message>|<tag>}. */
public record LoginError(String code, String message, String tag) {

    /** @return the parsed refusal, or null when {@code errorDescription} is absent */
    public static LoginError parse(String errorDescription) {
        if (errorDescription == null) return null;
        String[] parts = errorDescription.split("\\|");
        return new LoginError(parts.length > 0 ? parts[0] : null, parts.length > 1 ? parts[1] : null, parts.length > 2 ? parts[2] : null);
    }

    /** True when the user is logged in elsewhere and the refusal names the session ({@code tag}) to kill. */
    public boolean isAlreadyLoggedIn() {
        return AuthDefaults.CODE_ALREADY_LOGGED_IN.equals(code) && tag != null;
    }

    /** True for the transient "number of users allowed by your license has exceeded" refusal. */
    public boolean isLicenseExceeded() {
        return AuthDefaults.CODE_LICENSE_EXCEEDED.equals(code);
    }
}
