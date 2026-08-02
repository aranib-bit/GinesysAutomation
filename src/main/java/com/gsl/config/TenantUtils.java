package com.gsl.config;

/**
 * Tenant helper as a plain static utility, deliberately NOT a {@code default} method on
 * {@link ApiConfig} — Owner's dynamic proxy for {@code @Config} interfaces does not correctly
 * dispatch Java default methods (confirmed via a reproduced NPE: the proxy's InvocationHandler
 * treats every interface method, default or not, as a property lookup, returns null for one
 * that isn't a real configured key, and the primitive {@code boolean} return signature then
 * NPEs unboxing that null). Keep any derived/computed logic over {@link ApiConfig} values here,
 * not as a default method on the interface itself.
 */
public final class TenantUtils {

    private TenantUtils() {}

    public static boolean isMultiTenant(ApiConfig config, String tenant) {
        for (String t : config.multiTenantNames()) {
            if (t.equalsIgnoreCase(tenant)) return true;
        }
        return false;
    }
}
