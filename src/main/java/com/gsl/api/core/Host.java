package com.gsl.api.core;

/** Which API base an {@link Endpoint} lives under; the base URI itself always comes from {@code ApiConfig}, never from the endpoint. */
public enum Host {
    /** The original WebAPI base ({@code webApiBaseUri}, already carrying the session spec's base). */
    WEB_API,
    /** The Finance module's own API base ({@code financeApiBaseUri}). */
    FINANCE_API
}
