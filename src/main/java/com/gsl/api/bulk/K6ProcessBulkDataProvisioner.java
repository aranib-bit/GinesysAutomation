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
 * Launches one of k6's existing, unmodified performance scenarios via ProcessBuilder for
 * large-quantity backfill, blocking the calling test thread until it completes. k6's scope
 * stays performance-scenarios-only — this never adds a new script or mode to k6, bulk
 * documents are simply a byproduct of running a real performance scenario.
 */
public final class K6ProcessBulkDataProvisioner implements BulkDataProvisioner {

    // Matches the structured per-action console lines k6's own flow files already print, e.g.
    // "CreateItem [VU 2 iter 3] type=I barcode=... -> itemCode=... saveOk=true" — see
    // D:\PerformanceAutomationTesting\flows\{createItem,createGrc,createDc,serviceInvoiceAdhoc}.js.
    // No changes needed in the k6 project itself; this just reconstructs per-record report
    // nesting from output that was already structured.
    private static final Pattern K6_ACTION_LINE =
            Pattern.compile("^(CreateItem|CreateGrc|CreateDc|Save)\\s\\[VU (\\d+) iter (\\d+)\\]\\s(.*)$");

    private static BackfillStepLabel labelFor(String k6ActionPrefix) {
        return switch (k6ActionPrefix) {
            case "CreateItem" -> BackfillStepLabel.CREATE_ITEM;
            case "CreateGrc"  -> BackfillStepLabel.CREATE_GRC;
            case "CreateDc"   -> BackfillStepLabel.CREATE_DC;
            case "Save"       -> BackfillStepLabel.CREATE_SERVICE_INVOICE_ADHOC;
            default -> throw new IllegalStateException("Unmapped k6 action prefix: " + k6ActionPrefix);
        };
    }

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
