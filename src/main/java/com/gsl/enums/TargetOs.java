package com.gsl.enums;

import org.openqa.selenium.Platform;

/**
 * Operating system the remote browser (Grid node) runs on — selected with {@code -Dtarget.os=linux|windows}
 * (see {@code BrowserConfig#targetOs()}). Used to route a Grid session to a node of that OS through the
 * {@code platformName} capability, and to pick OS-specific settings such as the concurrent-session cap
 * ({@code max.concurrent.sessions.<os>}). Irrelevant to {@link RunMode#LOCAL}, which runs on this machine.
 */
public enum TargetOs {
    LINUX("linux", Platform.LINUX),
    /** Matches any Windows node (Windows 10/11/Server): {@code Platform.WIN11.is(Platform.WINDOWS)} is true. */
    WINDOWS("windows", Platform.WINDOWS);

    private final String key;
    private final Platform platform;

    TargetOs(String key, Platform platform) {
        this.key = key;
        this.platform = platform;
    }

    /** Lower-case config/property suffix: {@code linux} or {@code windows}. */
    public String key() {
        return key;
    }

    /** Selenium platform to request in the session capabilities. */
    public Platform platform() {
        return platform;
    }

    /**
     * Parses a config value case-insensitively; blank/null means {@link #LINUX} (the pre-existing Grid).
     *
     * @throws IllegalArgumentException for any other value, so a typo fails fast instead of silently running on Linux
     */
    public static TargetOs parse(String value) {
        if (value == null || value.isBlank()) {
            return LINUX;
        }
        String v = value.trim().toLowerCase();
        for (TargetOs os : values()) {
            if (os.key.equals(v)) {
                return os;
            }
        }
        throw new IllegalArgumentException("Unknown target.os '" + value + "'. Valid values: linux, windows.");
    }
}
