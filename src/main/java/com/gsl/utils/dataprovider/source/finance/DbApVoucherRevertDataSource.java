package com.gsl.utils.dataprovider.source.finance;

import com.gsl.database.DataAvailabilityGuard;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbQueryBuffer;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherTagState;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.List;

/**
 * DB-driven test-data source for {@code revertApVoucher} — plan: full DB-mode parity with General
 * Voucher's own {@code DbGeneralVoucherRevertDataSource}, but with a {@code {TAGGED, UNTAGGED}}
 * combination dimension ({@link ApVoucherTagState}) instead of GV's GL-account-type matrix — AP
 * Voucher's own Create-side combinations ({@code DbApVoucherDataSource}/{@code ApVoucherCombinations})
 * aren't relevant to Revert, which only needs an existing document, not a freshly-built one.
 *
 * <p>Single-phase (like {@code DbGeneralVoucherRevertDataSource}) — everything runs inside
 * {@link #load}, at {@code @DataSupplier} time (single-threaded, before TestNG's
 * {@code runInParallel} fan-out). Claim/backfill mechanics shared with
 * {@code DbApVoucherDeleteDataSource}/{@code DbApVoucherEditDataSource} via
 * {@link ApVoucherTagStateClaimSupport}.
 *
 * <ul>
 *   <li>{@code UNTAGGED} cell: claims a live Posted ({@code release_status='P'}) AP Voucher
 *       ({@code finvchmain.vchtype='P'} — {@link com.gsl.enums.VoucherType#AP}'s confirmed code)
 *       with NO {@code fintag} row on either {@code entcode1}/{@code entcode2}; backfills via
 *       {@code ApVoucherUntaggedBackfillStrategy} when none is free.</li>
 *   <li>{@code TAGGED} cell: claims a live Posted AP Voucher that DOES have a {@code fintag} row
 *       on either direction; on a shortfall, {@code ApVoucherTaggedBackfillStrategy} throws an
 *       honest {@link UnsupportedOperationException} rather than fabricating an untagged row
 *       mislabeled {@code TAGGED} — see that class's Javadoc.</li>
 * </ul>
 */
public class DbApVoucherRevertDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    private final String xmlGroupParam;

    /** No execution-group filter — every combination cell is emitted (full-suite run). */
    public DbApVoucherRevertDataSource() {
        this(null, null);
    }

    /** @param xmlGroupParam fallback {@code apv.group} value from TestNG {@code <parameter>} */
    public DbApVoucherRevertDataSource(String xmlGroupParam) {
        this(xmlGroupParam, null);
    }

    /** @param xmlGroupParam see {@link #DbApVoucherRevertDataSource(String)}
     *  @param xmlTagParam   unused today (no {@code apv.tag} dimension applies to Revert's own
     *                       {@link ApVoucherTagState} combination) — accepted for constructor-shape
     *                       parity with the other AP data sources' {@code (group, tag)} convention. */
    public DbApVoucherRevertDataSource(String xmlGroupParam, String xmlTagParam) {
        this.xmlGroupParam = xmlGroupParam;
    }

    @Override
    public List<MergedFinanceApVoucherData> load(String methodName) {
        ApVoucherExecutionGroup activeGroup = ApVoucherTagStateClaimSupport.resolveActiveGroup(xmlGroupParam);
        Jdbi jdbi = DatabaseManager.getJdbi();

        List<MergedFinanceApVoucherData> results = new ArrayList<>();
        if (ApVoucherTagStateClaimSupport.matchesGroup(activeGroup, ApVoucherExecutionGroup.MANDATORY)) {
            results.add(resolveOneCandidate(jdbi, ApVoucherTagState.UNTAGGED));
            results.add(resolveOneCandidate(jdbi, ApVoucherTagState.TAGGED));
        }

        TestDataSource.stampInvocations(results);
        int total = results.size();

        Log.info("[DB] ApVoucherRevert emitted {} row(s) for '{}' — group filter={}",
                results.size(), methodName, activeGroup);
        return results;
    }

    private static MergedFinanceApVoucherData resolveOneCandidate(Jdbi jdbi, ApVoucherTagState tagState) {
        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — POSTED AP VOUCHER CLAIM (REVERT, tagState=" + tagState + ")");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        DbQueryBuffer.start();
        FinVchMain header;
        try {
            header = DataAvailabilityGuard.selectOrBackfill(
                    () -> ApVoucherTagStateClaimSupport.claimOne(jdbi, tagState)
                            .orElseThrow(() -> new SkipException(
                                    "No documents found for the criteria: vchtype='P', release_status='P', "
                                    + "tagState=" + tagState + ", unclaimed this run — need 1.")),
                    1,
                    ApVoucherTagStateClaimSupport.backfillStrategyFor(jdbi, tagState));
        } finally {
            List<String> executedQueries = DbQueryBuffer.flush();
            if (!executedQueries.isEmpty()) {
                gatheringLog.add("");
                gatheringLog.add("QUERIES EXECUTED DURING DATA GATHERING");
                gatheringLog.addAll(executedQueries);
            }
        }

        gatheringLog.add("  Claimed voucher scheme_docno='" + header.getSchemeDocno()
                + "' (vchcode=" + header.getVchcode() + "), release_status='" + header.getReleaseStatus() + "'");
        gatheringLog.add("═══════════════════════════════════════════════════════════");

        return MergedFinanceApVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .dbGatheringLog(gatheringLog)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }
}
