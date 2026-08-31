package com.gsl.utils.dataprovider.source.finance;

import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbQueryBuffer;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.database.finance.GeneralVoucherApiBackfillStrategy;
import com.gsl.enums.Finance.Operations.Common.DocumentMessageBoxMessage;
import com.gsl.enums.GlAccountType;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.finance.FinVchMain;
import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherRevertOutcome;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherFixtureFactory;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherCombinationFilter;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherCombinations;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTag;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * DB-driven test-data source for {@code revertGeneralVoucher} — mirrors
 * {@link DbGeneralVoucherDataSource}'s use of {@link GeneralVoucherFixtureFactory} ×
 * {@link GeneralVoucherCombinations} (the same 128-combination Asset/Liability/Income/Expense ×
 * shape × side × reversal × payment matrix, filtered by {@code gv.group}/{@code gv.tag}), but
 * instead of building brand-new voucher data to create, each surviving combination resolves to an
 * <b>existing Posted ({@code release_status='P'}) General Voucher whose GL account type matches
 * the combination's {@link GlAccountType}, which has not already been bank-reconciled (no
 * {@code finvchdet} line with a non-null {@code brsdt}), and whose own accounting year/financial
 * month are both still open ({@link com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver#isEditable}
 * — a document in a closed period is rejected by the live app itself, so it's excluded here
 * rather than left for {@code RevertGeneralVoucher} to hit at runtime)</b>, to be reverted.
 *
 * <p>Single-phase (like {@code DbArVoucherDeleteDataSource}) — unlike Create's
 * {@link DbGeneralVoucherDataSource}, resolving a document to revert needs no live-browser-dependent
 * second phase, so the whole thing runs inside {@link #load}, at {@code @DataSupplier} time (single
 * threaded, before TestNG's {@code runInParallel} fan-out — see {@link #claimOnePostedVoucher}'s
 * Javadoc for why that matters).
 *
 * <p>When a combination's account type has no unclaimed Posted voucher left, the claim is wrapped
 * in {@link DataAvailabilityGuard#selectOrBackfill} with a
 * {@link GeneralVoucherApiBackfillStrategy}, exactly like every other DB-mode shortfall in this
 * codebase — see that strategy's Javadoc for its current (non-functional, pending-HAR) state.
 */
public class DbGeneralVoucherRevertDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    private final String xmlGroupParam;
    private final String xmlTagParam;

    public DbGeneralVoucherRevertDataSource() {
        this(null, null);
    }

    public DbGeneralVoucherRevertDataSource(String xmlGroupParam) {
        this(xmlGroupParam, null);
    }

    public DbGeneralVoucherRevertDataSource(String xmlGroupParam, String xmlTagParam) {
        this.xmlGroupParam = xmlGroupParam;
        this.xmlTagParam = xmlTagParam;
    }

    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {
        GeneralVoucherExecutionGroup activeGroup = GeneralVoucherCombinationFilter.resolveActiveGroup(xmlGroupParam);
        Set<GeneralVoucherTag> activeTags = GeneralVoucherCombinationFilter.resolveActiveTags(xmlTagParam);

        // Revert has no MIXED_TYPES analogue — GeneralVoucherFixtureFactory only ever returns
        // POSITIVE_COMBINATION for "revertGeneralVoucher", so this loop always runs the plain
        // combination sweep below (kept as a loop, mirroring DbGeneralVoucherDataSource's shape,
        // in case a second scenario type is ever added for Revert).
        List<GeneralVoucherScenarioType> scenarios = GeneralVoucherFixtureFactory.scenariosFor(methodName);

        Jdbi jdbi = DatabaseManager.getJdbi();
        // Vouchers already handed out to an earlier combination in THIS load() call — load() runs
        // single-threaded at @DataSupplier time (before TestNG's runInParallel fan-out starts any
        // test method body), so a plain HashSet is sufficient; no concurrent-claim race exists here.
        Set<Long> claimedThisRun = new HashSet<>();

        List<MergedFinanceGeneralVoucherData> results = new ArrayList<>();
        for (GeneralVoucherScenarioType scenario : scenarios) {
            for (GeneralVoucherVariant variant : GeneralVoucherCombinations.allCombinations()) {
                if (!GeneralVoucherCombinationFilter.survives(variant, activeGroup, activeTags)) continue;
                results.add(resolveOneRevertCandidate(jdbi, variant, claimedThisRun));
            }
        }

        // Negative scenario: a document whose own accounting year/financial month is already
        // closed — the live app rejects Revert outright (GeneralVoucherRevertOutcome
        // .CANNOT_REVERT_MONTH_CLOSED / DocumentMessageBoxMessage.REVERT_MONTH_ALREADY_CLOSED).
        // Deliberately NOT part of the GeneralVoucherVariant/GeneralVoucherCombinations matrix
        // above (that matrix is entirely GL-shape/side/reversal/payment combinatorics, unrelated
        // to accounting-period state) — always included exactly once, independent of
        // gv.group/gv.tag filtering, the same way CreateGeneralVoucherNegativeBoundary's negative
        // scenarios stand apart from the positive combination matrix.
        resolveClosedPeriodNegativeCandidate(jdbi, claimedThisRun).ifPresent(results::add);

        int total = results.size();
        for (int i = 0; i < total; i++) {
            results.get(i).setInvocationIndex(i + 1);
            results.get(i).setTotalInvocations(total);
        }

        Log.info("[DB] GeneralVoucherRevert emitted {} row(s) for '{}' — group filter={}, tag filter={}",
                results.size(), methodName, activeGroup, activeTags.isEmpty() ? "ALL" : activeTags);
        return results;
    }

    /**
     * Claims one Posted General Voucher matching {@code variant}'s account type (backfilling via
     * WebApi on a genuine shortfall) and builds the minimal
     * {@link MergedFinanceGeneralVoucherData} {@code RevertGeneralVoucher} needs — same field
     * shape {@code DbArVoucherDeleteDataSource} uses for its own minimal delete-mode DTO.
     */
    private static MergedFinanceGeneralVoucherData resolveOneRevertCandidate(
            Jdbi jdbi, GeneralVoucherVariant variant, Set<Long> claimedThisRun) {

        GlAccountType accountType = variant.getAccountType();
        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — POSTED VOUCHER CLAIM (GENERAL VOUCHER REVERT)");
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  Combination='" + variant.getVariantId() + "', required account type=" + accountType);

        DbQueryBuffer.start();
        Long vchcode;
        try {
            vchcode = DataAvailabilityGuard.selectOrBackfill(
                    () -> claimOnePostedVoucher(jdbi, accountType, claimedThisRun)
                            .orElseThrow(() -> new SkipException(
                                    "Skipped due to lack of data: no unclaimed Posted (release_status='P') "
                                    + "General Voucher found for account type=" + accountType + " — need 1.")),
                    1,
                    new GeneralVoucherApiBackfillStrategy(accountType));
        } finally {
            List<String> executedQueries = DbQueryBuffer.flush();
            if (!executedQueries.isEmpty()) {
                gatheringLog.add("");
                gatheringLog.add("───────────────────────────────────────────────────────────");
                gatheringLog.add("  QUERIES EXECUTED DURING DATA GATHERING");
                gatheringLog.add("───────────────────────────────────────────────────────────");
                gatheringLog.addAll(executedQueries);
            }
        }

        FinVchMain header = jdbi.onDemand(FinVchMainDao.class).findByVoucherCode(vchcode)
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed vchcode=" + vchcode + " vanished before its header could be re-read."));

        gatheringLog.add("  Claimed voucher scheme_docno='" + header.getSchemeDocno()
                + "' (vchcode=" + vchcode + "), release_status='" + header.getReleaseStatus() + "'");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        GeneralVoucherFixtureOptions opts = GeneralVoucherFixtureOptions.of(
                variant.getAccountType(), variant.getGlLineType(), variant.isDebitSide(), variant.isReversal(),
                variant.getPaymentVariant(), variant.getGlCount(), variant.getSiteCount());

        // documentInformation is deliberately NOT populated here — mirrors this class's own
        // design principle (see class Javadoc / plan §16): RevertGeneralVoucher's Step 1 "DB
        // Pre-Check" re-reads finvchmain live immediately before the UI runs (this data-source
        // row may go stale between @DataSupplier time and test execution) and is the sole place
        // that builds the report-facing DocumentInformation, for both DB-mode and Excel-mode rows
        // alike — see RevertGeneralVoucher.java.

        return MergedFinanceGeneralVoucherData.builder()
                .scenarioType(GeneralVoucherScenarioType.POSITIVE_COMBINATION)
                .scenarioTypeLabel(variant.getVariantId())
                .fixtureOptions(opts)
                .tags(GeneralVoucherCombinationFilter.tagsFor(variant))
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .reservedDocumentKey(String.valueOf(vchcode))
                .operationMode(GeneralVoucherOperationMode.REVERT)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    /**
     * Claims (marks as used, for the remainder of this {@code load()} call) one Posted
     * {@code finvchmain} row whose GL line's account type matches {@code type} — never the same
     * {@code vchcode} twice within one {@link #load} call, so two combinations sharing an account
     * type never revert the same document, and never a document that's already been bank-
     * reconciled ({@code finvchdet.brsdt IS NOT NULL} on any of its lines — reverting a BRS'd
     * document is blocked by the live app itself with a "cannot be reverted" dialog, so it's
     * excluded here rather than left for {@code RevertGeneralVoucher} to hit at runtime).
     *
     * <p><b>Closed-period guard</b> (mirrors {@code DbGeneralVoucherEditDataSource#claimOne}'s
     * own guard): a document sitting in a closed accounting year or closed financial month
     * cannot be reverted at all in the live app either — it shows "Finance month is already
     * closed. Cannot be reverted." instead of proceeding. The SQL above can't cheaply filter on
     * {@code admou_year}/{@code admou_month} itself, so each candidate's header is re-fetched and
     * checked via {@link AccountingPeriodResolver#isEditable} before being claimed — skip such
     * candidates rather than handing a positive test a document it could never actually revert.
     *
     * <p><b>No longer relies on "Revert never runs across parallel threads" alone</b> — that
     * assumption doesn't hold against {@code testng-gv-mandatory.xml}'s actual
     * {@code thread-count="2"} (Create/Revert run as parallel threads in that suite), and this
     * class's own {@code claimedThisRun} is local to one {@code load()} call, invisible to any
     * other concurrently-running test method's own claim. Every candidate accepted here is now
     * also passed through {@link DocumentReservationRegistry#tryReserve} — the same JVM-wide,
     * domain-agnostic reservation registry AR/AP's document-adjustment flows already use (released
     * automatically via {@code WebTest}'s own {@code @AfterMethod(alwaysRun=true)} teardown, which
     * {@code RevertGeneralVoucher} already inherits) — so a genuinely concurrent claim on the same
     * vchcode from another thread/method is rejected here rather than silently colliding at Save
     * time. Deliberately still a plain client-side filter rather than a full
     * {@code DbIdQueue}/{@code SuiteDataLoader} registration (the heavier mechanism AR Edit uses) —
     * General Voucher's DB-mode Create source ({@link DbGeneralVoucherDataSource}) already does its
     * own live ad-hoc JDBI queries rather than going through that queue machinery, and this
     * registry-backed filter closes the actual gap without that larger migration.
     */
    private static Optional<Long> claimOnePostedVoucher(
            Jdbi jdbi, GlAccountType type, Set<Long> claimedThisRun) {
        String sql =
            "SELECT DISTINCT m.vchcode FROM finvchmain m " +
            "JOIN finvchdet d ON d.vchcode = m.vchcode " +
            "JOIN fingl g ON g.glcode = d.glcode " +
            "WHERE m.vchtype = 'G' AND m.release_status = 'P' AND g.type = :type " +
            "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
            "ORDER BY m.vchcode";

        List<Long> candidates = jdbi.withHandle(handle ->
            handle.createQuery(sql)
                .bind("type", type.code())
                .mapTo(Long.class)
                .list()
        );

        FinVchMainDao headerDao = jdbi.onDemand(FinVchMainDao.class);
        for (Long vchcode : candidates) {
            if (claimedThisRun.contains(vchcode)) continue;

            FinVchMain header = headerDao.findByVoucherCode(vchcode).orElse(null);
            if (header == null) continue;
            if (!AccountingPeriodResolver.isEditable(jdbi, header, PeriodDomain.FINANCE)) continue;

            if (!DocumentReservationRegistry.tryReserve(String.valueOf(vchcode))) continue;

            claimedThisRun.add(vchcode);
            return Optional.of(vchcode);
        }
        return Optional.empty();
    }

    /**
     * Builds the one Revert negative scenario: claims an existing Posted General Voucher whose
     * own accounting year or financial month is already closed — exactly the condition
     * {@link #claimOnePostedVoucher}'s guard now excludes from every positive combination above
     * — and tags the resulting row with {@code expectedRevertOutcome=CANNOT_REVERT_MONTH_CLOSED}
     * so {@code RevertGeneralVoucher} asserts the live app actually refuses to revert it, rather
     * than merely happening to steer clear of one. Returns empty (logged, not thrown) if no
     * closed-period Posted candidate exists at all — there is no sensible backfill for
     * "manufacture a document in a closed accounting period", unlike the positive path's
     * {@link GeneralVoucherApiBackfillStrategy}, so a genuine shortfall here just skips the
     * negative scenario rather than failing the whole {@link #load} call.
     */
    private static Optional<MergedFinanceGeneralVoucherData> resolveClosedPeriodNegativeCandidate(
            Jdbi jdbi, Set<Long> claimedThisRun) {

        Optional<Long> claimed = claimOneClosedPeriodVoucher(jdbi, claimedThisRun);
        if (claimed.isEmpty()) {
            Log.warn("[DB] GeneralVoucherRevert: no closed-period Posted General Voucher available for the "
                    + "CANNOT_REVERT_MONTH_CLOSED negative scenario — skipping it (nothing to backfill against).");
            return Optional.empty();
        }

        long vchcode = claimed.get();
        FinVchMain header = jdbi.onDemand(FinVchMainDao.class).findByVoucherCode(vchcode)
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed closed-period vchcode=" + vchcode + " vanished before its header could be re-read."));

        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — CLOSED-PERIOD VOUCHER CLAIM (GENERAL VOUCHER REVERT, NEGATIVE)");
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  Claimed voucher scheme_docno='" + header.getSchemeDocno()
                + "' (vchcode=" + vchcode + "), release_status='" + header.getReleaseStatus()
                + "' — own accounting year/financial month CONFIRMED closed; expecting Revert to be REJECTED "
                + "with: \"" + DocumentMessageBoxMessage.REVERT_MONTH_ALREADY_CLOSED.getText() + "\"");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        // documentInformation deliberately not populated here — same reasoning as
        // resolveOneRevertCandidate: RevertGeneralVoucher's Step 1 "DB Pre-Check" re-reads
        // finvchmain live and is the sole builder of the report-facing DocumentInformation.
        // fixtureOptions deliberately left null — this row isn't part of the GL-shape combination
        // matrix; GeneralVoucherDbValidator/GeneralVoucherDescriptionBuilder both already treat a
        // null fixtureOptions as "not a combination-matrix row" and handle it gracefully.
        return Optional.of(MergedFinanceGeneralVoucherData.builder()
                .scenarioTypeLabel("GV_REVERT_NEGATIVE_MONTH_CLOSED")
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .reservedDocumentKey(String.valueOf(vchcode))
                .operationMode(GeneralVoucherOperationMode.REVERT)
                .expectedRevertOutcome(GeneralVoucherRevertOutcome.CANNOT_REVERT_MONTH_CLOSED)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build());
    }

    /**
     * Claims one Posted {@code finvchmain} row whose own accounting year OR financial month is
     * already CLOSED — the exact inverse of {@link #claimOnePostedVoucher}'s guard, used solely
     * to build the one {@code CANNOT_REVERT_MONTH_CLOSED} negative scenario above. Same
     * not-already-BRS'd exclusion and {@link DocumentReservationRegistry} reservation as the
     * positive claim, so this can never collide with a positive combination's own claim within
     * the same {@link #load} call or across concurrently-running test methods.
     */
    private static Optional<Long> claimOneClosedPeriodVoucher(Jdbi jdbi, Set<Long> claimedThisRun) {
        String sql =
            "SELECT DISTINCT m.vchcode FROM finvchmain m " +
            "WHERE m.vchtype = 'G' AND m.release_status = 'P' " +
            "AND NOT EXISTS (SELECT 1 FROM finvchdet d2 WHERE d2.vchcode = m.vchcode AND d2.brsdt IS NOT NULL) " +
            "ORDER BY m.vchcode";

        List<Long> candidates = jdbi.withHandle(handle -> handle.createQuery(sql).mapTo(Long.class).list());

        FinVchMainDao headerDao = jdbi.onDemand(FinVchMainDao.class);
        for (Long vchcode : candidates) {
            if (claimedThisRun.contains(vchcode)) continue;

            FinVchMain header = headerDao.findByVoucherCode(vchcode).orElse(null);
            if (header == null) continue;
            // Inverse of the positive guard: we WANT a closed year/month here.
            if (AccountingPeriodResolver.isEditable(jdbi, header, PeriodDomain.FINANCE)) continue;

            if (!DocumentReservationRegistry.tryReserve(String.valueOf(vchcode))) continue;

            claimedThisRun.add(vchcode);
            return Optional.of(vchcode);
        }
        return Optional.empty();
    }
}
