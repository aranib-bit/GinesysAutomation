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

    @SuppressWarnings("unchecked")
    public static <T extends Config> T getOrCreate(Class<T> type) {
        return (T) CACHE.computeIfAbsent(type,
                k -> org.aeonbits.owner.ConfigFactory.create(type));
    }
}
