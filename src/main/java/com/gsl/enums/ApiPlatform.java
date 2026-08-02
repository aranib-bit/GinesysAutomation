package com.gsl.enums;

/**
 * The sub-division of the API test-execution context — used by ApiSessionManager for
 * context switching (WEBAPI/PUBLICAPI, real switchable sessions) and by DataSourceRouter for
 * backfill routing (WEBAPI/PUBLICAPI/K6BULK).
 */
public enum ApiPlatform {
    /** Internal API the ExtJS/Angular web app itself calls (same endpoints k6 hits). UserPool-backed. */
    WEBAPI,
    /** Ginesys's published partner/public REST API — separate base URL, OAuth2 client-credentials. */
    PUBLICAPI,
    /**
     * k6's existing performance scenarios, launched via ProcessBuilder — bulk/large-quantity
     * backfill only. Never a live session: ApiSessionManager keeps a map slot for it but
     * ContextOrchestrator never switches into it — only DataSourceRouter/DataAvailabilityGuard
     * select it, routing straight to K6ProcessBulkDataProvisioner.
     */
    K6BULK
}
