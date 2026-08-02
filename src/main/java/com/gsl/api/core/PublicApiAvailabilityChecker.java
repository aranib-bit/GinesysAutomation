package com.gsl.api.core;

import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;
import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * One reachability check per JVM run (not per test) for PUBLICAPI — gates whether
 * DataSourceRouter is even allowed to pick it for backfill, and whether ApiSessionFactory
 * should bother trying to build a PUBLICAPI session at all.
 */
public final class PublicApiAvailabilityChecker {

    private static volatile Boolean cached;

    private PublicApiAvailabilityChecker() {}

    public static boolean isAvailable() {
        if (!ConfigFactory.getApiConfig().publicApiEnabled()) {
            return false; // tenant may not have PUBLICAPI provisioned at all
        }
        if (cached != null) {
            return cached;
        }
        cached = pingTokenEndpoint();
        return cached;
    }

    private static boolean pingTokenEndpoint() {
        try {
            String tokenUrl = ConfigFactory.getApiConfig().publicApiTokenUrl();
            Response response = RestAssured.given().baseUri(tokenUrl).get();
            boolean reachable = response.getStatusCode() < 500;
            ReportLogger.network("[PublicApiAvailabilityChecker] Reachability check — status={}, reachable={}",
                    response.getStatusCode(), reachable);
            return reachable;
        } catch (Exception e) {
            ReportLogger.networkWarn("[PublicApiAvailabilityChecker] PUBLICAPI unreachable: {}", e.getMessage());
            return false;
        }
    }
}
