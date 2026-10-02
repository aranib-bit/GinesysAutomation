package com.gsl.api.bulk;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.api.core.DataSetupException;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.BackfillStepLabel;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link BulkDataProvisioner} implementation that shells out to k6 (the
 * {@code D:\PerformanceAutomationTesting} performance framework) via {@link ProcessBuilder},
 * launching one of k6's existing, unmodified performance scenarios for large-quantity backfill
 * and blocking the calling test thread until it completes. k6's scope stays
 * performance-scenarios-only — this never adds a new script or mode to k6; bulk documents are
 * simply a byproduct of running a real performance scenario.
 *
 * <p>While the k6 process runs, its stdout is streamed line-by-line into the calling test's
 * ExtentReport (see {@link #captureOutputToReport(Process)}), so a bulk backfill shows up as
 * nested per-record steps rather than one opaque "ran k6" log line.
 */
public final class K6ProcessBulkDataProvisioner implements BulkDataProvisioner {

    // Matches the structured per-action console lines k6's own flow files already print, e.g.
    // "CreateItem [VU 2 iter 3] type=I barcode=... -> itemCode=... saveOk=true" — see
    // D:\PerformanceAutomationTesting\flows\{createItem,createGrc,createDc,serviceInvoiceAdhoc}.js.
    // No changes needed in the k6 project itself; this just reconstructs per-record report
    // nesting from output that was already structured.
    private static final Pattern K6_ACTION_LINE =
            Pattern.compile("^(CreateItem|CreateGrc|CreateDc|Save)\\s\\[VU (\\d+) iter (\\d+)\\]\\s(.*)$");

    /** Maps a {@link #K6_ACTION_LINE} action prefix (e.g. {@code "CreateItem"}) to its report step label.
     *  @throws IllegalStateException if {@code k6ActionPrefix} isn't one of the mapped prefixes */
    private static BackfillStepLabel labelFor(String k6ActionPrefix) {
        return switch (k6ActionPrefix) {
            case "CreateItem" -> BackfillStepLabel.CREATE_ITEM;
            case "CreateGrc"  -> BackfillStepLabel.CREATE_GRC;
            case "CreateDc"   -> BackfillStepLabel.CREATE_DC;
            case "Save"       -> BackfillStepLabel.CREATE_SERVICE_INVOICE_ADHOC;
            default -> throw new IllegalStateException("Unmapped k6 action prefix: " + k6ActionPrefix);
        };
    }

    /**
     * Resolves {@code scriptKey} to its k6 script path via {@link ApiConfig#k6ScriptPathFor(String)},
     * launches it as a child process with {@code TARGET_DOC_COUNT} and {@code TENANT} environment
     * variables set, streams its combined stdout/stderr into the report as it runs (see
     * {@link #captureOutputToReport(Process)}), and blocks until the process exits.
     *
     * @param scriptKey which k6 scenario script to run (see {@link BulkDataProvisioner#provisionAndWait})
     * @param quantity  how many records the run should produce; forwarded as {@code TARGET_DOC_COUNT}
     * @throws DataSetupException if the k6 process exits non-zero, or if it can't be started/awaited
     */
    @Override
    public void provisionAndWait(String scriptKey, int quantity) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        String scriptPath = cfg.k6ScriptPathFor(scriptKey);

        ProcessBuilder pb = new ProcessBuilder(cfg.k6BinaryPath(), "run", scriptPath);
        pb.environment().put("TARGET_DOC_COUNT", String.valueOf(quantity));
        pb.environment().put("TENANT", cfg.activeTenant());
        pb.redirectErrorStream(true);

        ReportLogger.dataBackfillWarn("[K6ProcessBulkDataProvisioner] Launching k6 for {} record(s): {}",
                quantity, String.join(" ", pb.command()));

        try {
            Process process = pb.start();
            captureOutputToReport(process);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new DataSetupException("[K6ProcessBulkDataProvisioner] k6 run exited " + exitCode
                        + " — bulk backfill of " + quantity + " record(s) failed");
            }
            ReportLogger.dataBackfill("[K6ProcessBulkDataProvisioner] k6 run completed successfully for {} record(s)", quantity);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new DataSetupException("[K6ProcessBulkDataProvisioner] Failed to run k6 bulk provisioning", e);
        }
    }

    /**
     * Reads {@code process}'s combined stdout/stderr line by line, forwarding every line to the
     * data-backfill report log; lines matching {@link #K6_ACTION_LINE} additionally get nested
     * under a per-VU/iteration "Record" group node (created lazily, once per VU/iter pair) instead
     * of being logged flat, mirroring the WebAPI-path strategies' per-record report nesting.
     * A no-op if no ExtentTest is currently active (see {@link ExtentManager#hasTest()}).
     */
    private void captureOutputToReport(Process process) throws IOException {
        // Keyed by "VU-iter" (insertion order preserved) so each k6 virtual-user/iteration pair
        // gets its own "Record" group node the first time one of its action lines is seen,
        // mirroring the WebAPI-path strategies' per-record nesting.
        Map<String, ExtentTest> recordNodes = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = K6_ACTION_LINE.matcher(line);
                if (m.matches() && ExtentManager.hasTest()) {
                    String vu = m.group(2);
                    String iter = m.group(3);
                    String recordKey = vu + "-" + iter;
                    ExtentTest recordNode = recordNodes.computeIfAbsent(recordKey,
                            k -> StepExecutor.group("Record (VU " + vu + ", iter " + iter + ")"));
                    recordNode.createNode(labelFor(m.group(1)).label()).info(line);
                } else {
                    ReportLogger.dataBackfill("[k6] {}", line);
                }
            }
        }
    }
}
