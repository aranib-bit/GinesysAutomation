package com.gsl.api.bulk;

import com.aventstack.extentreports.ExtentTest;
import com.gsl.api.core.DataSetupException;
import com.gsl.api.integration.DocSchemeProvisioner;
import com.gsl.config.ApiConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.enums.VoucherDomain;
import com.gsl.model.adm.AdmDocSchemeCandidate;
import com.gsl.reports.ExtentManager;
import com.gsl.reports.ReportLogger;
import com.gsl.reports.StepExecutor;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeResolver;
import org.jdbi.v3.core.Jdbi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills a dedicated small-width TEST document scheme to its cap by running k6 with parallel users, so the
 * "Save on a full scheme" regression has a scheme with no numbers left without creating ~100k real
 * vouchers. Never touches a real scheme: the scheme must be listed in {@code docscheme.test.names} and the
 * environment in {@code docscheme.provision.allowed.envs} (see {@link DocSchemeProvisioner}).
 *
 * <p>Flow: read the scheme's {@code remaining} from the DB → run k6 {@code fillDocScheme.js} for exactly
 * that many documents → re-read the DB; if numbers are still left (some iterations failed) run ONE top-up
 * pass → report. k6 only creates the documents; whether the scheme is really full is decided by the DB,
 * never by k6's own counts.
 *
 * <p>Report: one foldable group "🧰 Fill Doc Scheme" under the current test with Plan / Execution (one fold
 * per VU) / k6 summary / Verification nodes — see {@link #fillToCap}.
 */
public final class DocSchemeFiller {

    private DocSchemeFiller() {}

    public enum Status { ALREADY_FULL, PASS, WARN, FAIL }

    /** Outcome of {@link #fillToCap}. {@code WARN} = full, but only after a top-up pass; {@code FAIL} = still not full. */
    public record FillResult(String docName, long docCode, long remainingBefore, long remainingAfter,
                             int created, int failed, int passes, Status status) {
        /** @throws DataSetupException unless the scheme ended up full. */
        public void requireFull() {
            if (remainingAfter > 0) {
                throw new DataSetupException("[DocSchemeFiller] '" + docName + "' still has " + remainingAfter
                        + " number(s) left after " + passes + " k6 pass(es) (created=" + created
                        + ", failed=" + failed + ") — not full.");
            }
        }
    }

    // k6 flow output (fillDocScheme.js): "FillVoucher [VU 3 iter 12] OK type=GV docNo=VG/001/26-27 status=201 ms=345"
    //                                    "FillVoucher [VU 3 iter 12] FAIL type=GV status=400 ms=120 msg=..."
    private static final Pattern FILL_LINE =
            Pattern.compile("^FillVoucher \\[VU (\\d+) iter (\\d+)\\] (OK|FAIL)(?: (.*))?$");
    private static final Pattern SUMMARY_LINE = Pattern.compile("^K6_SUMMARY created=(\\d+) failed=(\\d+).*$");
    private static final int TAIL_LINES = 40;

    /**
     * @param domain        GV / AR / AP — selects the k6 flow and the menu the scheme is read for
     * @param docName       the TEST scheme to fill (must be in {@code docscheme.test.names})
     * @param ownerSiteCode owner site the vouchers use (decides OU/year for the counter lookup)
     * @param siteCodes     the user's assigned sites (bounds which schemes are visible)
     */
    public static FillResult fillToCap(VoucherDomain domain, String docName, long ownerSiteCode, List<Long> siteCodes) {
        DocSchemeProvisioner.assertEnvironmentAllowed();
        DocSchemeProvisioner.assertTestSchemeName(docName);

        ApiConfig cfg = ConfigFactory.getApiConfig();
        Jdbi jdbi = DatabaseManager.getJdbi();
        String shortcode = domain.getMenuShortcode();

        AdmDocSchemeCandidate before = DocSchemeResolver.find(jdbi, shortcode, siteCodes, ownerSiteCode, docName)
                .orElseThrow(() -> new DataSetupException("[DocSchemeFiller] Doc scheme '" + docName
                        + "' is not mapped to " + shortcode + " AND enabled for the user's sites " + siteCodes
                        + " — run DocSchemeProvisioner first."));
        if (before.getRemaining() == null) {
            throw new DataSetupException("[DocSchemeFiller] Cannot fill '" + docName + "': capacity is not tracked "
                    + "for its reset interval '" + before.getGenerationInterval() + "'.");
        }
        long remainingBefore = before.getRemaining();

        boolean reporting = ExtentManager.hasTest();
        ExtentTest root = reporting
                ? StepExecutor.group("🧰 Fill Doc Scheme — " + docName + " (doccode " + before.getDoccode() + ")")
                : null;
        String tenant = cfg.docSchemeFillK6Tenant().isBlank() ? cfg.activeTenant() : cfg.docSchemeFillK6Tenant();
        int vus = Math.max(1, cfg.docSchemeFillVus());

        List<String> plan = List.of(
                "Voucher type : " + domain + " (menu " + shortcode + ")",
                "Scheme       : '" + docName + "' doccode=" + before.getDoccode() + ", width=" + before.getWidth()
                        + ", lastno=" + before.getLastUsed() + ", remaining=" + remainingBefore,
                "Target       : fill to cap — " + remainingBefore + " document(s) across up to " + vus + " parallel user(s)",
                "k6 tenant    : " + tenant + " (api tenant " + cfg.activeTenant() + ")");
        log(root, "Plan", plan);

        if (remainingBefore <= 0) {
            log(root, "Verification", List.of("Already full — nothing to create."));
            return new FillResult(docName, before.getDoccode(), 0, 0, 0, 0, 0, Status.ALREADY_FULL);
        }

        int created = 0;
        int failed = 0;
        int passes = 0;
        long remaining = remainingBefore;
        while (remaining > 0 && passes < 2) {   // pass 1 + at most one top-up
            passes++;
            PassOutcome outcome = runPass(root, cfg, domain, docName, tenant, remaining, vus, passes);
            created += outcome.created;
            failed += outcome.failed;
            remaining = DocSchemeResolver.find(jdbi, shortcode, siteCodes, ownerSiteCode, docName)
                    .map(AdmDocSchemeCandidate::getRemaining).orElse(remaining);
            ReportLogger.dataBackfill("[DocSchemeFiller] pass {} done — k6 created={}, failed={}, DB remaining={}",
                    passes, outcome.created, outcome.failed, remaining);
        }

        Status status = remaining > 0 ? Status.FAIL : (passes > 1 ? Status.WARN : Status.PASS);
        log(root, "Verification", List.of(
                "After-state (DB): remaining=" + remaining + " → " + (remaining == 0 ? "FULL ✔" : "NOT FULL ✘"),
                "k6 created=" + created + ", failed=" + failed + " over " + passes + " pass(es) → " + status));
        if (root != null) {
            ExtentTest verification = root.createNode("Final status");
            switch (status) {
                case PASS -> verification.pass("Scheme is full after " + passes + " pass");
                case WARN -> verification.warning("Scheme is full, but only after a top-up pass (some k6 iterations failed)");
                default -> verification.fail("Scheme still has " + remaining + " number(s) left after " + passes + " pass(es)");
            }
        }
        return new FillResult(docName, before.getDoccode(), remainingBefore, remaining, created, failed, passes, status);
    }

    // ── one k6 pass ──────────────────────────────────────────────────────────

    private record PassOutcome(int created, int failed, int exitCode) {}

    private static PassOutcome runPass(ExtentTest root, ApiConfig cfg, VoucherDomain domain, String docName,
                                       String tenant, long needed, int vus, int pass) {
        int detailLimit = cfg.docSchemeFillReportDetailLimit();
        boolean perDocument = needed <= detailLimit;

        Map<String, String> env = new LinkedHashMap<>();
        env.put("TARGET_DOCS", String.valueOf(needed));
        env.put("TARGET_VUS", String.valueOf((int) Math.min(vus, needed)));
        env.put("MAX_DURATION", cfg.docSchemeFillMaxDuration());
        env.put("TENANT", tenant);
        env.put("VOUCHER_TYPE", domain.name());
        env.put("DOC_SCHEME_NAME", docName);

        ExtentTest execution = root == null ? null
                : root.createNode("Execution — pass " + pass + " (" + needed + " doc(s), " + env.get("TARGET_VUS") + " VU)");
        Map<Integer, VuStats> perVu = new TreeMap<>();
        int[] k6Summary = {-1, -1};
        Deque<String> tail = new ArrayDeque<>();
        long start = System.currentTimeMillis();

        int exit = new K6ProcessBulkDataProvisioner().run("fillDocScheme", env, line -> {
            tail.addLast(line);
            if (tail.size() > TAIL_LINES) tail.removeFirst();

            FillLine fill = parseFillLine(line);
            if (fill != null) {
                VuStats stats = perVu.computeIfAbsent(fill.vu(), v -> new VuStats(
                        execution == null ? null : execution.createNode("VU " + v)));
                if (fill.ok()) stats.ok++; else stats.failed++;
                if (stats.node != null && (perDocument || !fill.ok())) {
                    String text = "iter " + fill.iter() + " " + (fill.ok() ? "OK" : "FAIL") + " " + fill.detail();
                    if (fill.ok()) stats.node.info(text); else stats.node.fail(text);
                }
                return;
            }
            int[] totals = parseSummary(line);
            if (totals != null) {
                k6Summary[0] = totals[0];
                k6Summary[1] = totals[1];
                return;
            }
            ReportLogger.dataBackfill("[k6] {}", line);
        });
        long seconds = (System.currentTimeMillis() - start) / 1000;

        int ok = perVu.values().stream().mapToInt(v -> v.ok).sum();
        int bad = perVu.values().stream().mapToInt(v -> v.failed).sum();
        if (k6Summary[0] >= 0) { ok = k6Summary[0]; bad = k6Summary[1]; }   // k6's own totals win when printed

        perVu.forEach((vu, stats) -> {
            if (stats.node != null) {
                stats.node.info("VU " + vu + " — " + stats.ok + " ok / " + stats.failed + " failed"
                        + (perDocument ? "" : " (per-document lines hidden above " + detailLimit + " docs; failures always shown)"));
            }
        });

        if (root != null) {
            ExtentTest summary = root.createNode("k6 summary — pass " + pass);
            String line = "exit code=" + exit + ", duration=" + seconds + "s, created=" + ok + ", failed=" + bad;
            if (exit == 0 && bad == 0) summary.pass(line); else summary.warning(line);
            summary.info("k6 output tail (last " + TAIL_LINES + " lines):<br/><pre>"
                    + String.join("\n", tail).replace("<", "&lt;") + "</pre>");
        }
        return new PassOutcome(ok, bad, exit);
    }

    /** One parsed {@code FillVoucher [VU n iter m] OK|FAIL ...} line from fillDocScheme.js. */
    record FillLine(int vu, int iter, boolean ok, String detail) {}

    /** @return the parsed line, or {@code null} if {@code line} (already unwrapped from k6's log format) is not one. */
    static FillLine parseFillLine(String line) {
        Matcher m = FILL_LINE.matcher(line);
        if (!m.matches()) return null;
        return new FillLine(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                "OK".equals(m.group(3)), m.group(4) == null ? "" : m.group(4));
    }

    /** @return {@code [created, failed]} from a {@code K6_SUMMARY} line, or {@code null} if {@code line} is not one. */
    static int[] parseSummary(String line) {
        Matcher m = SUMMARY_LINE.matcher(line);
        return m.matches() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
    }

    private static final class VuStats {
        final ExtentTest node;
        int ok;
        int failed;
        VuStats(ExtentTest node) { this.node = node; }
    }

    private static void log(ExtentTest root, String nodeName, List<String> lines) {
        lines.forEach(l -> ReportLogger.dataBackfill("[DocSchemeFiller] {}", l));
        if (root != null) {
            root.createNode(nodeName).info("<pre>" + String.join("\n", new ArrayList<>(lines)) + "</pre>");
        }
    }
}
