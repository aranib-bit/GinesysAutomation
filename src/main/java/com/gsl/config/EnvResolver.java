package com.gsl.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import com.gsl.enums.TestEnvironment;

/**
 * Decides which target environment (staging, clone-staging, feature-05, ...) this JVM runs against
 * and publishes it as the {@code env} variable that every config interface's
 * {@code env/${env}/<name>.properties} source and the per-environment Excel folder resolve from.
 *
 * <p>Lookup order, first match wins:
 * <ol>
 *   <li>{@code -Denv=<name>} JVM system property (Maven passes it through {@code argLine})</li>
 *   <li>{@code env} operating-system environment variable</li>
 *   <li>{@code env.local} file in the project root ({@code env=<name>}, gitignored — for IDE runs)</li>
 *   <li>{@link #DEFAULT_ENV}</li>
 * </ol>
 * The result is validated once: the name must be a plain folder name and
 * {@code src/test/resources/env/<name>/} must exist, otherwise the run fails fast with a message
 * naming the source that chose it. Resolution happens on first use and is called from
 * {@link ConfigCache} before any Owner proxy is created.
 */
public final class EnvResolver {

    /** Environment used when nothing selects one. */
    public static final String DEFAULT_ENV = TestEnvironment.STAGING.folder();

    private static final String KEY = "env";
    private static final String LOCAL_FILE = "env.local";

    private static TestEnvironment resolvedEnvironment;
    private static String resolvedName;
    private static String resolvedSource;

    private EnvResolver() {}

    /** The active environment name, resolved and validated on first call. */
    public static synchronized String current() {
        if (resolvedName == null) {
            resolve();
        }
        return resolvedName;
    }

    /** The active environment as an enum constant. */
    public static synchronized TestEnvironment environment() {
        current();
        return resolvedEnvironment;
    }

    /** Human-readable origin of the active environment ("-Denv", "env variable", "env.local" or "default"). */
    public static synchronized String source() {
        current();
        return resolvedSource;
    }

    /** Project-relative classpath-style folder of the active environment, e.g. {@code env/feature-05}. */
    public static String resourceFolder() {
        return "env/" + current();
    }

    /** Absolute path of the active environment's folder under {@code src/test/resources}. */
    public static Path envDir() {
        return envDirOf(current());
    }

    private static Path envDirOf(String name) {
        return Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "env", name);
    }

    private static void resolve() {
        String name = clean(System.getProperty(KEY));
        String source = "-Denv";
        if (name == null) {
            name = clean(System.getenv(KEY));
            source = "env variable";
        }
        if (name == null) {
            name = readLocalFile();
            source = LOCAL_FILE;
        }
        if (name == null) {
            name = DEFAULT_ENV;
            source = "default";
        }

        TestEnvironment environment = TestEnvironment.from(name, source);
        name = environment.folder();
        if (!Files.isDirectory(envDirOf(name))) {
            throw new IllegalStateException("Environment '" + name + "' (from " + source + ") is a known environment"
                    + " but has no folder at " + envDirOf(name) + ". Create it under src/test/resources/env/.");
        }

        resolvedEnvironment = environment;
        resolvedName = name;
        resolvedSource = source;
        System.setProperty(KEY, name);
        org.aeonbits.owner.ConfigFactory.setProperty(KEY, name);
        Secrets.publishConfigSecrets();
        System.out.println("[EnvResolver] Target environment: " + name + " (from " + source + ")");
    }

    private static String readLocalFile() {
        Path file = Paths.get(System.getProperty("user.dir"), LOCAL_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties props = new Properties();
            props.load(reader);
            return clean(props.getProperty(KEY));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
