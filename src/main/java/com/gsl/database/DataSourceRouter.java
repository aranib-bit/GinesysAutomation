package com.gsl.database;

import com.gsl.api.core.PublicApiAvailabilityChecker;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.ApiPlatform;

/**
 * Decides which source backfills a data-availability shortfall: k6 (bulk, large quantity) or
 * WEBAPI/PUBLICAPI (small quantity, in config-driven preference order — WEBAPI first by
 * default, PUBLICAPI as fallback; flip the order in api.properties, no code change needed).
 */
public final class DataSourceRouter {

    private DataSourceRouter() {}

    public static ApiPlatform decide(int neededQuantity) {
        int smallQtyThreshold = ConfigFactory.getApiConfig().smallQuantityThreshold();
        if (neededQuantity > smallQtyThreshold) {
            return ApiPlatform.K6BULK;
        }

        for (ApiPlatform candidate : ConfigFactory.getApiConfig().smallQtyPreferredOrder()) {
            if (isAvailable(candidate)) {
                return candidate;
            }
        }
        return ApiPlatform.WEBAPI; // ultimate fallback — internal API, assumed always reachable
    }

    private static boolean isAvailable(ApiPlatform candidate) {
        return switch (candidate) {
            case WEBAPI -> true; // internal API — always available whenever UserPool has API users
            case PUBLICAPI -> PublicApiAvailabilityChecker.isAvailable();
            case K6BULK -> true; // not reachable from this branch — large quantity already routed above
        };
    }
}
