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
 * DB-driven test-data source for {@code deleteApVoucher} — mirrors
 * {@link DbApVoucherRevertDataSource}'s exact shape (claim/backfill mechanics shared via
 * {@link ApVoucherTagStateClaimSupport}), one {@code TAGGED} row and one {@code UNTAGGED} row per
 * run, filtered by {@code apv.group}. Unlike Revert, {@code DeleteApVoucher} itself never
 * re-reads {@code release_status} live (it mirrors {@code DeleteArVoucher}'s own convention of
 * trusting the data-source-supplied value directly), so {@code releaseStatus} is populated here
 * from the same claim query's own captured header — no separate live re-read step is needed.
 */
public class DbApVoucherDeleteDataSource implements TestDataSource<MergedFinanceApVoucherData> {

    private final String xmlGroupParam;

    public DbApVoucherDeleteDataSource() {
        this(null);
    }

    /** @param xmlGroupParam fallback {@code apv.group} value from TestNG {@code <parameter>} */
    public DbApVoucherDeleteDataSource(String xmlGroupParam) {
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

        int total = results.size();
        for (int i = 0; i < total; i++) {
            results.get(i).setInvocationIndex(i + 1);
            results.get(i).setTotalInvocations(total);
        }

        Log.info("[DB] ApVoucherDelete emitted {} row(s) for '{}' — group filter={}",
                results.size(), methodName, activeGroup);
        return results;
    }

    private static MergedFinanceApVoucherData resolveOneCandidate(Jdbi jdbi, ApVoucherTagState tagState) {
        List<String> gatheringLog = new ArrayList<>();
        gatheringLog.add("═══════════════════════════════════════════════════════════");
        gatheringLog.add("  DB DATA GATHERING — AP VOUCHER CLAIM (DELETE, tagState=" + tagState + ")");
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
