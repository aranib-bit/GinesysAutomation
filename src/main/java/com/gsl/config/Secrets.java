package com.gsl.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Looks up secrets (passwords, client secrets) that must not live in committed files.
 *
 * <p>Lookup order for a key such as {@code user.default.password}, first match wins:
 * <ol>
 *   <li>{@code -D<key>=...} JVM system property</li>
 *   <li>operating-system environment variable named after the key, upper-cased with every
 *       non-alphanumeric character replaced by {@code _} ({@code USER_DEFAULT_PASSWORD}) —
 *       what Jenkins credential bindings provide</li>
 *   <li>the active environment's gitignored {@code env/<env>/secrets.local.properties}</li>
 * </ol>
 * The Owner config interfaces read the same {@code secrets.local.properties} (and system properties)
 * through their {@code @Sources}, so config-bound secrets and this class share one local file.
 */
public final class Secrets {

    private static final String LOCAL_FILE = "secrets.local.properties";
    private static volatile Properties local;

    /** Config keys whose values are secrets; published as system properties so the Owner interfaces read them. */
    private static final String[] CONFIG_KEYS = {
            "apppassword", "databasepassword", "publicapi.clientSecret",
            "remote.app.server.winrm.password", "remote.app.server.ssh.password",
            "remote.db.server.winrm.password", "remote.db.server.ssh.password"
    };

    private Secrets() {}

    /**
     * Copies every {@link #CONFIG_KEYS} secret found in an environment variable or secrets.local.properties
     * into a system property (unless -D already set it), so the Owner interfaces' {@code system:properties}
     * source delivers them. Called once by {@link EnvResolver} after the environment is chosen.
     */
    static void publishConfigSecrets() {
        for (String key : CONFIG_KEYS) {
            if (System.getProperty(key) == null) {
                find(key).ifPresent(v -> System.setProperty(key, v));
            }
        }
    }

    /** The secret for {@code key}, or empty when none of the three sources defines it. */
    public static Optional<String> find(String key) {
        String value = System.getProperty(key);
        if (isSet(value)) return Optional.of(value);

        value = System.getenv(key.toUpperCase().replaceAll("[^A-Z0-9]", "_"));
        if (isSet(value)) return Optional.of(value);

        value = localProperties().getProperty(key);
        return isSet(value) ? Optional.of(value) : Optional.empty();
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private static Properties localProperties() {
        Properties props = local;
        if (props == null) {
            synchronized (Secrets.class) {
                props = local;
                if (props == null) {
                    props = load();
                    local = props;
                }
            }
        }
        return props;
    }

    private static Properties load() {
        Properties props = new Properties();
        Path file = EnvResolver.envDir().resolve(LOCAL_FILE);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + file + ": " + e.getMessage(), e);
            }
        }
        return props;
    }
}
