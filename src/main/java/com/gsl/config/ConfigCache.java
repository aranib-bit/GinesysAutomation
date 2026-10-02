package com.gsl.config;

import org.aeonbits.owner.Config;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton cache for Owner config instances.
 * Prevents repeated reflection-based proxy creation on every ConfigFactory call.
 * Thread-safe via ConcurrentHashMap.computeIfAbsent — one instance per Config type.
 */
public final class ConfigCache {

    private static final ConcurrentHashMap<Class<?>, Object> CACHE = new ConcurrentHashMap<>();

    private ConfigCache() {}

    /**
     * Returns the cached singleton instance for {@code type}, creating it via
     * {@link org.aeonbits.owner.ConfigFactory#create(Class, java.util.Map[])} on first request.
     * Called from {@link ConfigFactory}'s per-config-type getters — not meant to be called
     * directly with an arbitrary {@link Config} subtype elsewhere.
     *
     * @param type the {@code @Config}-annotated interface to instantiate/reuse a proxy for
     * @return the shared proxy instance for {@code type}
     */
    @SuppressWarnings("unchecked")
    public static <T extends Config> T getOrCreate(Class<T> type) {
        EnvResolver.current(); // publishes ${env} for the env/${env}/ sources before any proxy is built
        return (T) CACHE.computeIfAbsent(type,
                k -> org.aeonbits.owner.ConfigFactory.create(type));
    }
}
