package com.gsl.driver.provider;

import com.gsl.enums.RunMode;

import java.util.EnumMap;

/**
 * Maps each RunMode to its ExecutionProvider implementation.
 * Add a new provider by inserting one line in the static initializer — no other code changes needed.
 */
public final class ExecutionProviderRegistry {

    private static final EnumMap<RunMode, ExecutionProvider> REGISTRY = new EnumMap<>(RunMode.class);

    static {
        REGISTRY.put(RunMode.LOCAL,        new LocalExecutionProvider());
        REGISTRY.put(RunMode.REMOTE,       new SeleniumGridProvider());
        REGISTRY.put(RunMode.DOCKER_GRID,  new DockerGridProvider());
        REGISTRY.put(RunMode.BROWSERSTACK, new BrowserStackProvider());
        REGISTRY.put(RunMode.LAMBDATEST,   new LambdaTestProvider());
        REGISTRY.put(RunMode.MOBILE,       new AppiumProvider());
    }

    private ExecutionProviderRegistry() {}

    public static ExecutionProvider get(RunMode mode) {
        ExecutionProvider provider = REGISTRY.get(mode);
        if (provider == null) {
            throw new IllegalArgumentException("[ExecutionProviderRegistry] No provider registered for RunMode: " + mode);
        }
        return provider;
    }
}
