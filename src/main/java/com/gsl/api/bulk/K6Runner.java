package com.gsl.api.bulk;

import com.gsl.api.core.DataSetupException;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Launches one k6 scenario and reports what happened: checks first that the k6 binary, the script and the tenant file exist (a precise
 * {@link DataSetupException} instead of a failure deep inside k6), streams every output line to the caller with k6's own log wrapping removed,
 * and maps the exit code to a {@link K6Status}. It knows nothing about reports or backfill; those are the callers' concern.
 */
public final class K6Runner {

    /** The scenario's final totals line, e.g. {@code K6_SUMMARY created=20 failed=1}. */
    private static final Pattern SUMMARY_LINE = Pattern.compile("^K6_SUMMARY created=(\\d+) failed=(\\d+).*$");
    private static final int TAIL_LINES = 40;
    private static final String TENANT_ENV = "TENANT";
    private static final String USERS_ENV = "USERS_FILE";

    /** Binaries whose {@code k6 version} already answered in this JVM. */
    private static final Set<String> VERIFIED_BINARIES = ConcurrentHashMap.newKeySet();

    private final String binary;
    private final Path home;
    /** Writes the users file for a tenant and returns its path; null = the caller always supplies {@code USERS_FILE} itself (unit tests). */
    private final java.util.function.Function<String, Path> usersProvider;

    public K6Runner(String binary, Path home) {
        this(binary, home, null);
    }

    public K6Runner(String binary, Path home, java.util.function.Function<String, Path> usersProvider) {
        this.binary = binary;
        this.home = home;
        this.usersProvider = usersProvider;
    }

    /** A runner for the configured binary and k6 project directory. */
    public static K6Runner configured() {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        return new K6Runner(cfg.k6BinaryPath(), K6Scripts.home(), K6UsersExport::exportFor);
    }

    /**
     * Runs {@code script} with {@code env} added to the child's environment and hands each output line to {@code lineHandler}. Never throws on a
     * non-zero exit; the {@link K6Result} carries it.
     *
     * @throws DataSetupException when a precondition fails or the process cannot be started or awaited
     */
    public K6Result run(Path script, Map<String, String> env, Consumer<String> lineHandler) {
        preflight(script, env.get(TENANT_ENV));
        ProcessBuilder pb = new ProcessBuilder(binary, "run", script.toString());
        pb.environment().putAll(withUsersFile(env));
        pb.redirectErrorStream(true);
        Deque<String> tail = new ArrayDeque<>();
        int[] totals = {-1, -1};
        Process process = null;
        try {
            process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String raw;
                while ((raw = reader.readLine()) != null) {
                    String line = K6ProcessBulkDataProvisioner.unwrapConsoleLine(raw);
                    Matcher summary = SUMMARY_LINE.matcher(line);
                    if (summary.matches()) {
                        totals[0] = Integer.parseInt(summary.group(1));
                        totals[1] = Integer.parseInt(summary.group(2));
                    }
                    if (tail.size() == TAIL_LINES) tail.removeFirst();
                    tail.addLast(line);
                    lineHandler.accept(line);
                }
            }
            int exit = process.waitFor();
            return new K6Result(exit, K6Status.fromExitCode(exit), totals[0], totals[1], java.util.List.copyOf(tail));
        } catch (IOException | InterruptedException e) {
            if (process != null) process.destroyForcibly();
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DataSetupException("[K6Runner] Failed to run k6 (" + script.getFileName() + ")", e);
        }
    }

    /** {@code env} plus {@code USERS_FILE} (the environment's PERF users, exported from UsersList.xlsx) when the caller did not name one. */
    private Map<String, String> withUsersFile(Map<String, String> env) {
        String tenant = env.get(TENANT_ENV);
        if (usersProvider == null || env.containsKey(USERS_ENV) || tenant == null || tenant.isBlank()) return env;
        Map<String, String> withUsers = new java.util.LinkedHashMap<>(env);
        withUsers.put(USERS_ENV, usersProvider.apply(tenant).toAbsolutePath().toString());
        return withUsers;
    }

    private void preflight(Path script, String tenant) {
        if (!Files.isRegularFile(script)) {
            throw new DataSetupException("[K6Runner] k6 script not found: " + script + " (k6.home=" + home + ")");
        }
        if (tenant != null && !tenant.isBlank()) {
            Path tenantFile = home.resolve("erp").resolve("config").resolve("environments").resolve(tenant + ".json");
            if (!Files.isRegularFile(tenantFile)) {
                throw new DataSetupException("[K6Runner] No k6 tenant file for '" + tenant + "': expected " + tenantFile
                        + ". Add it, or set the k6 tenant for this environment (e.g. docscheme.fill.k6.tenant).");
            }
        }
        verifyBinary();
    }

    private void verifyBinary() {
        if (VERIFIED_BINARIES.contains(binary)) return;
        try {
            Process p = new ProcessBuilder(binary, "version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (p.waitFor() != 0) {
                throw new DataSetupException("[K6Runner] '" + binary + " version' failed; check datafallback.k6.binaryPath");
            }
            VERIFIED_BINARIES.add(binary);
        } catch (IOException e) {
            throw new DataSetupException("[K6Runner] k6 binary '" + binary + "' cannot be started (" + e.getMessage()
                    + "); install k6 or set datafallback.k6.binaryPath", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataSetupException("[K6Runner] Interrupted while checking the k6 binary", e);
        }
    }
}
