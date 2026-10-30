package com.gsl.api.bulk;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.api.core.DataSetupException;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.reports.BackfillStepLabel;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link BulkDataProvisioner} implementation that launches a k6 scenario of this project ({@code performance/k6}, see {@link K6Scripts})
 * through {@link K6Runner} for large-quantity backfill and blocks the calling test thread until it completes.
 *
 * <p>While the k6 process runs, its stdout is streamed line-by-line into the calling test's
 * ExtentReport (see {@link #reportLineHandler()}), so a bulk backfill shows up as
 * nested per-record steps rather than one opaque "ran k6" log line.
 */
public final class K6ProcessBulkDataProvisioner implements BulkDataProvisioner {

    // Matches the structured per-action console lines k6's own flow files already print, e.g.
    // "CreateItem [VU 2 iter 3] type=I barcode=... -> itemCode=... saveOk=true" — see
    // performance/k6/flows/{createItem,createGrc,createDc,serviceInvoiceAdhoc}.js.
    // No changes needed in the k6 project itself; this just reconstructs per-record report
    // nesting from output that was already structured.
    private static final Pattern K6_ACTION_LINE =
            Pattern.compile("^(CreateItem|CreateGrc|CreateDc|Save)\\s\\[VU (\\d+) iter (\\d+)\\]\\s(.*)$");

    // k6 wraps every console.log in its own log format, e.g.
    //   time="2026-10-05T15:59:43+05:30" level=info msg="FillVoucher [VU 1 iter 0] OK type=AR ..." source=console   (non-TTY)
    //   INFO[0001] FillVoucher [VU 1 iter 0] OK type=AR ...  source=console                                       (TTY)
    private static final Pattern K6_LOGFMT_MSG =
            Pattern.compile("\\smsg=\"((?:[^\"\\\\]|\\\\.)*)\"(?:\\s+\\w+=\\S+)*\\s*$");
    private static final Pattern K6_TTY_MSG =
            Pattern.compile("^(?:INFO|WARN|ERRO|DEBU)\\[\\d+\\]\\s+(.*?)\\s+source=console\\s*$");

    /**
     * Returns the plain {@code console.log} text from a line k6 printed, undoing k6's own log wrapping
     * (see above); a line that is not wrapped is returned unchanged. Lets structured flow lines such as
     * {@code FillVoucher [VU n iter m] OK ...} be matched the same way however k6 was launched.
     */
    public static String unwrapConsoleLine(String line) {
        Matcher logfmt = K6_LOGFMT_MSG.matcher(line);
        if (logfmt.find()) {
            return logfmt.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        Matcher tty = K6_TTY_MSG.matcher(line);
        return tty.matches() ? tty.group(1) : line;
    }

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
     * Resolves {@code scriptKey} to its k6 script path via {@link K6Scripts#resolve(String)},
     * launches it as a child process with {@code TARGET_DOCS} and {@code TENANT} environment
     * variables set, streams its combined stdout/stderr into the report as it runs (see
     * {@link #reportLineHandler()}), and blocks until the process exits.
     *
     * @param scriptKey which k6 scenario script to run (see {@link BulkDataProvisioner#provisionAndWait})
     * @param quantity  how many records the run should produce; forwarded as {@code TARGET_DOCS}
     * @throws DataSetupException if the k6 process exits non-zero, or if it can't be started/awaited
     */
    @Override
    public void provisionAndWait(String scriptKey, int quantity) {
        ApiConfig cfg = ConfigFactory.getApiConfig();
        // TARGET_DOCS is the name the k6 scenarios actually read (scenarios/backfill/targetDocs.js,
        // itemGrcDcTransferOutChainTargetDocs.js, fillDocScheme.js). This used to be sent as
        // TARGET_DOC_COUNT, which no k6 script reads, so the requested quantity was silently ignored.
        Map<String, String> env = Map.of("TARGET_DOCS", String.valueOf(quantity), "TENANT", cfg.activeTenant());
        ReportLogger.dataBackfillWarn("[K6ProcessBulkDataProvisioner] Launching k6 '{}' for {} record(s) (env {})", scriptKey, quantity, env);

        K6Result result = K6Runner.configured().run(K6Scripts.resolve(scriptKey), env, reportLineHandler());
        if (!result.passed()) {
            throw new DataSetupException("[K6ProcessBulkDataProvisioner] k6 '" + scriptKey + "' ended " + result.getStatus() + " (exit "
                    + result.getExitCode() + ") - bulk backfill of " + quantity + " record(s) failed"
                    + (result.hasSummary() ? "; k6 reported created=" + result.getCreated() + " failed=" + result.getFailed() : ""));
        }
        ReportLogger.dataBackfill("[K6ProcessBulkDataProvisioner] k6 run completed successfully for {} record(s){}", quantity,
                result.hasSummary() ? " (k6 created=" + result.getCreated() + ", failed=" + result.getFailed() + ")" : "");
    }

    /**
     * Runs the k6 script for {@code scriptKey} with {@code extraEnv} added to the child's environment,
     * hands every combined stdout/stderr line to {@code lineHandler} as it arrives, and blocks until the
     * process exits. Unlike {@link #provisionAndWait} it never throws on a non-zero exit - the caller
     * decides what a failed/partial run means (e.g. {@code DocSchemeFiller} re-reads the DB instead).
     *
     * @return the process exit code
     * @throws DataSetupException if a precondition fails (see {@link K6Runner}) or the process can't be started or awaited
     */
    public int run(String scriptKey, Map<String, String> extraEnv, java.util.function.Consumer<String> lineHandler) {
        ReportLogger.dataBackfillWarn("[K6ProcessBulkDataProvisioner] Launching k6 '{}' (env {})", scriptKey, extraEnv);
        return K6Runner.configured().run(K6Scripts.resolve(scriptKey), extraEnv, lineHandler).getExitCode();
    }

    /**
     * Forwards every k6 output line to the data-backfill report log; lines matching {@link #K6_ACTION_LINE} additionally get nested
     * under a per-VU/iteration "Record" group node (created lazily, once per VU/iter pair) instead
     * of being logged flat, mirroring the WebAPI-path strategies' per-record report nesting.
     */
    private java.util.function.Consumer<String> reportLineHandler() {
        Map<String, ExtentTest> recordNodes = new LinkedHashMap<>();
        return line -> {
            Matcher m = K6_ACTION_LINE.matcher(line);
            if (m.matches() && ExtentManager.hasTest()) {
                String vu = m.group(2);
                String iter = m.group(3);
                ExtentTest recordNode = recordNodes.computeIfAbsent(vu + "-" + iter,
                        k -> StepExecutor.group("Record (VU " + vu + ", iter " + iter + ")"));
                recordNode.createNode(labelFor(m.group(1)).label()).info(line);
            } else {
                ReportLogger.dataBackfill("[k6] {}", line);
            }
        };
    }
}
