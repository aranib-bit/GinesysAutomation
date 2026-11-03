package com.gsl.approval.service;

/**
 * The approval configuration could not be read or is not usable. Carries a {@link Kind} so a report shows whether it was authentication, a
 * missing resource, a server fault or a malformed answer - and so none of them is ever treated as "approval disabled".
 */
public class ApprovalConfigurationException extends RuntimeException {

    /** Why the configuration is unusable. */
    public enum Kind {
        AUTHENTICATION, AUTHORIZATION, NOT_FOUND, SERVER, COMMUNICATION, INVALID_RESPONSE, ENTITY_MISMATCH,
        /** The answer was well-formed but the configuration it describes cannot be used (no rules, inactive hierarchy, level gap...). */
        INVALID_CONFIGURATION
    }

    private final Kind kind;

    public ApprovalConfigurationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ApprovalConfigurationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /** Maps an HTTP status (0 = no response) to the failure kind it means. */
    public static Kind kindForStatus(int status) {
        if (status == 401) return Kind.AUTHENTICATION;
        if (status == 403) return Kind.AUTHORIZATION;
        if (status == 404) return Kind.NOT_FOUND;
        if (status >= 500) return Kind.SERVER;
        if (status == 0) return Kind.COMMUNICATION;
        return Kind.INVALID_RESPONSE;
    }
}
