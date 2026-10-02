package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.MenuOperationKey;
import com.gsl.database.DbIdQueue;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.enums.PeriodDomain;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditDateVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodCache;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads AR Voucher test data for <b>Edit</b> operations by querying an existing
 * voucher from {@code finvchmain} + {@code finvchdet}.
 *
 * <h3>Data flow</h3>
 * <ol>
 *   <li>Polls one {@code vchcode} from {@link DbIdQueue} key
 *       {@code "FinanceARVoucher.Edit"} — guaranteed unique per thread.</li>
 *   <li>Fetches the voucher header ({@code finvchmain}) and detail rows
 *       ({@code finvchdet}).</li>
 *   <li>Resolves site name, GL name, SL names via existing DAOs.</li>
 *   <li>Builds {@link MergedFinanceArVoucherData} with:
 *       <ul>
 *         <li>{@code existingVoucherNo} — the {@code scheme_docno} used to locate
 *             the voucher in the UI list page.</li>
 *         <li>{@code documentInformation} — existing site/GL with a <em>new</em>
 *             document date, reference, and remarks.</li>
 *         <li>{@code subLedgerInformationList} — existing SL rows (amounts unchanged;
 *             the Edit test verifies they are preserved after save).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Scope</h3>
 * Targets vouchers with {@code release_status IN ('N','U','P')}.
 * For posted vouchers ({@code 'P'}) the test class handles unpost + fintag check
 * before applying edits.
 */
public class DbArVoucherEditDataSource implements TestDataSource<MergedFinanceArVoucherData> {

    /**
     * Claims one open-period voucher via {@link #claimOpenPeriodCandidate}, resolves its site/GL/
     * detail rows, and builds the single-row {@link MergedFinanceArVoucherData}
     * {@code EditArVoucher} needs — see the class Javadoc's "Data flow" for the full shape.
     */
    @Override
    public List<MergedFinanceArVoucherData> load(String testMethodName) {
        Jdbi jdbi = DatabaseManager.getJdbi();

        final FinVchMain header = claimOpenPeriodCandidate(jdbi);
        final long vchcode = header.getVchcode();

        Log.info("[DB-Edit] Fetching AR Voucher for Edit: vchcode={}", vchcode);

        // 2. Site name (admsite.name WHERE code = admsite_code_owner)
        String siteName = jdbi.withHandle(h ->
                h.createQuery("SELECT name FROM admsite WHERE code = :code")
                 .bind("code", header.getAdmsiteCodeOwner())
                 .mapTo(String.class)
                 .findFirst()
                 .orElseThrow(() -> new IllegalStateException(
                         "[DbArVoucherEditDataSource] admsite not found for admsite_code_owner=" + header.getAdmsiteCodeOwner()))
        );

        // 3. GL name
        FinGl gl = jdbi.onDemand(FinGlDao.class).findByGlCode(header.getGlcode());
        if (gl == null) throw new IllegalStateException(
                "[DbArVoucherEditDataSource] fingl not found for glcode=" + header.getGlcode());

        // 4. Detail rows
        List<FinVchDet> details = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(vchcode);
        if (details.isEmpty()) {
            Log.warn("[DB-Edit] No finvchdet rows found for vchcode={} — SL list will be empty.", vchcode);
        }

        // 5. Resolve SL info for each detail row
        List<SubLedgerInformation> slRows = resolveSlRows(jdbi, details);

        // 6. Build DocumentInformation with NEW date / reference / remarks (same site + GL)
        // documentdate is accounting-year-aware (via AccountingPeriodResolver) rather than a bare
        // LocalDate.now() — the same fix applied to GV's Edit data sources, since an unconditional
        // "today" fails to save once the claimed voucher's own accounting year doesn't bracket
        // today (see the "GV Edit: accounting-year-aware dates..." plan). RANDOM_MID is the
        // "typical", always-valid choice — AR has no per-row date-variant/negative-case matrix
        // yet (out of scope here; only this correctness fix is applied for parity).
        long ouCode = AccountingPeriodResolver.resolveOuCode(jdbi, header.getAdmsiteCodeOwner());
        LocalDate newDate = AccountingPeriodResolver.resolveEditDate(
                jdbi, header.getYcode(), ouCode, VoucherEditDateVariant.RANDOM_MID, PeriodDomain.FINANCE);

        DocumentInformation docInfo = new DocumentInformation(
                header.getSchemeDocno(),          // testCaseId — reuse scheme_docno for traceability
                siteName,                          // ownersite
                header.getSchemeDocno(),           // voucherno — existing doc number
                gl.getGlname(),                    // cashbank
                FakerUtils.randomRemarks(),        // remarks — NEW value
                newDate,                            // documentdate — NEW value, accounting-year-aware
                FakerUtils.randomReference()       // reference — NEW value
        );

        // 7. MasterTestData — login credentials from config
        MasterTestData master = new MasterTestData(
                testMethodName, null, null, null,
                "FIN-AR-EDIT-" + vchcode, 1);

        MergedFinanceArVoucherData editData = MergedFinanceArVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .currentDocDate(header.getVchdt())
                .masterTestData(master)
                .documentInformation(docInfo)
                .subLedgerInformationList(slRows)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .paymentProvided(false)
                .needsPaymentInfoWarning(false)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();

        Log.info("[DB-Edit] Built edit data for vchcode={}, scheme_docno='{}', new date={}, {} SL row(s)",
                vchcode, header.getSchemeDocno(), docInfo.getDocumentdate(), slRows.size());

        return List.of(editData);
    }

    /**
     * Pulls candidates off {@link DbIdQueue} key {@code "FinanceARVoucher.Modify"} until one
     * whose own accounting year AND financial month are both still open is found (closed-period
     * guard, mirroring {@code DbGeneralVoucherEditDataSource#claimOne}'s own guard) — a document
     * sitting in a closed period can't be edited at all in the live app, so it's skipped here
     * rather than left for {@code EditArVoucher} to hit an app rejection at runtime. {@link DbIdQueue}
     * has no "peek" API, so each popped id's header is fetched and checked via
     * {@link AccountingPeriodResolver#isEditable} before being accepted; a rejected id is simply
     * discarded (not returned to the queue) since {@code DbIdQueue}'s contract is pop-once-only.
     *
     * <p><b>Does NOT prefer the current accounting year</b> the way the GV data sources now do
     * (see the "GV Revert picks documents in a now-closed accounting period, + closed-period
     * negative-test coverage" plan, follow-up round 2) — deliberately, not an oversight.
     * {@code DbIdQueue} is a shared, pop-once-only FIFO drawn from by every concurrently-running
     * AR Edit test thread; discarding a perfectly valid open-period candidate just because it
     * isn't in the current year (holding out for a better one) would permanently shrink the pool
     * for every other consumer with no safe way to put it back, unlike GV's data sources (which
     * re-run their own live, freely-rescannable ad-hoc query per claim). The candidate actually
     * accepted is still logged against today's resolved current year below, so a report reader
     * can see whether it happened to be current-year or not.
     */
    private static FinVchMain claimOpenPeriodCandidate(Jdbi jdbi) {
        // Memoizes the OU/year/month lookups isEditable below would otherwise re-query per
        // rejected id popped from the queue — see AccountingPeriodCache's own Javadoc. Scoped to
        // this one method call, not shared across separate claimOpenPeriodCandidate invocations.
        AccountingPeriodCache periodCache = new AccountingPeriodCache();
        while (true) {
            Long vchcode = DbIdQueue.getNextId(MenuOperationKey.build("FinanceARVoucher", "Modify"));
            if (vchcode == null) {
                throw new SkipException(
                        "No documents found for the criteria: vchtype='R', release_status IN ('N','U','P'), "
                        + "own accounting year/financial month open (DbIdQueue key "
                        + "'FinanceARVoucher.Modify' exhausted).");
            }

            FinVchMain header = jdbi.onDemand(FinVchMainDao.class)
                    .findByVoucherCode(vchcode)
                    .orElseThrow(() -> new IllegalStateException(
                            "[DbArVoucherEditDataSource] finvchmain row not found for vchcode=" + vchcode));

            if (periodCache.isEditable(jdbi, header, PeriodDomain.FINANCE)) {
                logCurrentYearComparison(jdbi, header, periodCache);
                return header;
            }
            Log.info("[DB-Edit] Skipping vchcode={} — its own accounting year/financial month is closed.", vchcode);
        }
    }

    /** Informational only (see {@link #claimOpenPeriodCandidate}'s Javadoc for why this doesn't
     *  change acceptance) — logs whether the accepted candidate happens to be in today's
     *  resolved current accounting year. {@code periodCache} is the same instance
     *  {@link #claimOpenPeriodCandidate} already resolved this header's OU code with, reused here
     *  instead of re-querying {@code admsite_ou} for the same {@code admsiteCodeOwner}. */
    private static void logCurrentYearComparison(Jdbi jdbi, FinVchMain header, AccountingPeriodCache periodCache) {
        long ouCode = periodCache.resolveOuCode(jdbi, header.getAdmsiteCodeOwner());
        AccountingPeriodResolver.findCurrentYear(jdbi, LocalDate.now(), ouCode).ifPresentOrElse(
                currentYear -> Log.info("[DB-Edit] Claimed vchcode={} (ycode={}) — {} today's current admyear "
                        + "ycode={} '{}' for admou_code={}.",
                        header.getVchcode(), header.getYcode(),
                        header.getYcode() == currentYear.getYcode() ? "matches" : "does NOT match",
                        currentYear.getYcode(), currentYear.getYname(), ouCode),
                () -> Log.info("[DB-Edit] Claimed vchcode={} (ycode={}) — no open admyear found containing "
                        + "today for admou_code={}.", header.getVchcode(), header.getYcode(), ouCode));
    }

    /**
     * Resolves each {@code finvchdet} row into a {@link SubLedgerInformation} by
     * looking up the SL name + SL ID from {@code finsl} and the GL name from {@code fingl}.
     */
    private static List<SubLedgerInformation> resolveSlRows(Jdbi jdbi, List<FinVchDet> details) {
        FinSlDao slDao = jdbi.onDemand(FinSlDao.class);
        FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
        List<SubLedgerInformation> rows = new ArrayList<>();
        // Finding D (Round 12 continued): local per-call cache — a multi-line voucher with several
        // finvchdet rows sharing the same glcode/slcode used to re-issue the identical fingl/finsl
        // lookup once per line with zero memoization; see DbGeneralVoucherEditDataSource
        // #resolveExpenseIncomeRows for the identical pattern and its live evidence.
        Map<Long, FinSl> slCache = new HashMap<>();
        Map<Long, FinGl> glCache = new HashMap<>();

        for (int i = 0; i < details.size(); i++) {
            FinVchDet det = details.get(i);

            FinSl sl = slCache.computeIfAbsent(det.getSlcode(), slDao::findBySlcode);
            FinGl gl = glCache.computeIfAbsent(det.getGlcode(), glDao::findByGlCode);

            String slName = (sl != null) ? sl.getSlname() : "SL-" + det.getSlcode();
            String slId   = (sl != null) ? sl.getSlid()   : null;
            String glName = (gl != null) ? gl.getGlname() : "GL-" + det.getGlcode();

            // Use camount for credit-side rows (AR voucher credits the SL)
            rows.add(new SubLedgerInformation(
                    /* testCaseID      */ null,
                    /* lineNo          */ String.valueOf(i + 1),
                    /* subLedger       */ slName,
                    /* id              */ slId,
                    /* generalLedger   */ glName,
                    /* referenceSite   */ null,
                    /* ledgerNarration */ det.getNartext(),
                    /* isReversal      */ "N",
                    /* amountCr        */ det.getCamount(),
                    /* adjustmentType  */ null,
                    /* paymentSelection*/ null
            ));
        }
        return rows;
    }
}
