package com.gsl.api.bulk;

import com.gsl.api.core.DataSetupException;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.ReportLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Launches one of k6's existing, unmodified performance scenarios via ProcessBuilder for
 * large-quantity backfill, blocking the calling test thread until it completes. k6's scope
 * stays performance-scenarios-only — this never adds a new script or mode to k6, bulk
 * documents are simply a byproduct of running a real performance scenario.
 */
public final class K6ProcessBulkDataProvisioner implements BulkDataProvisioner {

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

    private void captureOutputToReport(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ReportLogger.dataBackfill("[k6] {}", line);
            }
        }
    }
}
