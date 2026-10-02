package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.BackfillStrategy;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DocumentReservationRegistry;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.HrEmpOuDao;
import com.gsl.database.finance.ApVoucherTaggedBackfillStrategy;
import com.gsl.database.finance.ApVoucherUntaggedBackfillStrategy;
import com.gsl.model.finance.FinVchMain;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.ApVoucherTagState;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Optional;

/**
 * Shared claim/backfill/group-filter mechanics for {@code DbApVoucherRevertDataSource}/
 * {@code DbApVoucherDeleteDataSource}/{@code DbApVoucherEditDataSource} — the three siblings all
 * claim one live Posted AP Voucher per {@link ApVoucherTagState} combination cell (same table,
 * same {@code fintag} both-directions detection, same {@code apv.group} gate), so this one class
 * holds that common mechanics rather than tripling it across three near-identical copies. Package-
 * private by convention (all three callers live in this same package) would also be reasonable,
 * but kept {@code public} for consistency with this codebase's other cross-class support helpers
 * (e.g. {@code AccountingPeriodResolver}).
 */
public final class ApVoucherTagStateClaimSupport {

    private ApVoucherTagStateClaimSupport() {}

    /**
     * Claims one Posted ({@code release_status='P'}) AP Voucher ({@code finvchmain.vchtype='P'} —
     * {@link com.gsl.enums.VoucherType#AP}'s confirmed code) matching {@code tagState} — for
     * {@link ApVoucherTagState#TAGGED}, one whose {@code vchcode} appears as either
     * {@code fintag.entcode1} or {@code entcode2}; for {@link ApVoucherTagState#UNTAGGED}, one
     * with no such row on either side. {@code fintag.entcode1}/{@code entcode2} are already
     * denormalized directly to {@code finvchmain.vchcode} (see {@code FinTag}'s field Javadocs),
     * so this is a direct {@code EXISTS}/{@code NOT EXISTS} against {@code fintag} — no join
     * through {@code finpost} is needed. Reserves the winning candidate via
     * {@link DocumentReservationRegistry} so a genuinely concurrent claim on the same
     * {@code vchcode} from another thread/method is rejected rather than silently colliding later.
     */
    public static Optional<FinVchMain> claimOne(Jdbi jdbi, ApVoucherTagState tagState) {
        return claimOne(jdbi, tagState, null);
    }

    /**
     * {@link #claimOne(Jdbi, ApVoucherTagState)}, additionally filtered by Advance-vs-Document
     * payment mode — added for {@code editApVoucherDetailLine}'s own Advance-mode scenario
     * (plain, directly-editable Amount field), which the mode-agnostic 2-arg overload can't target
     * deterministically (that query claims *any* Posted voucher matching the tag predicate,
     * Advance or Document alike, since {@code fintag} naturally has 0 rows for Advance either way).
     *
     * @param onAccountYes {@code null} = no mode filter (identical to the 2-arg overload);
     *                     {@code true} = Advance-mode only ({@code isadvance='Y'});
     *                     {@code false} = Document-mode only ({@code isadvance='N'})
     */
    public static Optional<FinVchMain> claimOne(Jdbi jdbi, ApVoucherTagState tagState, Boolean onAccountYes) {
        String tagPredicate = tagState == ApVoucherTagState.TAGGED
                ? "EXISTS (SELECT 1 FROM fintag t WHERE t.entcode1 = m.vchcode OR t.entcode2 = m.vchcode)"
                : "NOT EXISTS (SELECT 1 FROM fintag t WHERE t.entcode1 = m.vchcode OR t.entcode2 = m.vchcode)";
        String modePredicate = onAccountYes == null ? ""
                : " AND m.isadvance = '" + (onAccountYes ? "Y" : "N") + "'";

        String sql = "SELECT m.* FROM finvchmain m "
                + "WHERE m.vchtype = 'P' AND m.release_status = 'P' AND " + tagPredicate + modePredicate + " "
                + "ORDER BY m.vchcode DESC";

        List<FinVchMain> candidates = jdbi.withHandle(handle ->
                handle.createQuery(sql).mapToBean(FinVchMain.class).list());

        for (FinVchMain header : candidates) {
            if (!DocumentReservationRegistry.tryReserve(String.valueOf(header.getVchcode()))) continue;
            return Optional.of(header);
        }
        return Optional.empty();
    }

    /**
     * Resolves the {@link BackfillStrategy} for a shortfall on {@code tagState} — the real,
     * working {@link ApVoucherUntaggedBackfillStrategy} for {@code UNTAGGED} (resolving the
     * current config user's own site/OU/ecode to construct it), or the honest-stub
     * {@link ApVoucherTaggedBackfillStrategy} for {@code TAGGED} (see that class's Javadoc for why
     * no real implementation exists yet).
     */
    public static BackfillStrategy backfillStrategyFor(Jdbi jdbi, ApVoucherTagState tagState) {
        if (tagState == ApVoucherTagState.TAGGED) {
            return new ApVoucherTaggedBackfillStrategy();
        }
        String loginName = ConfigFactory.getConfig().username();
        long ecode = jdbi.onDemand(HrEmpDao.class).findEcodeByAbbre(loginName)
                .orElseThrow(() -> new IllegalStateException(
                        "No hrdemp record found for login user: '" + loginName + "'. Check appusername in config.properties."));
        List<Long> siteCodes = jdbi.onDemand(HrEmpOuDao.class).findSiteCodesByEcode(ecode);
        if (siteCodes.isEmpty()) {
            throw new IllegalStateException("User '" + loginName + "' (ecode=" + ecode + ") has no site assignments in hrdemp_ou.");
        }
        int ownerSiteCode = siteCodes.get(0).intValue();
        // Mirrors ApVoucherArBackfillStrategy's own call-site convention (same site code passed for
        // both ownerSiteCode and connectedOUCode) — see that class's construction site in
        // DbArVoucherDataSource.
        return new ApVoucherUntaggedBackfillStrategy(ownerSiteCode, ownerSiteCode, ecode);
    }

    /** Resolves the active {@code apv.group} execution-group filter from (1) the system property
     *  (Maven/CI path) or (2) {@code xmlGroupParam} (IntelliJ direct-run path). {@code null} means
     *  "run all groups". Mirrors {@code DbApVoucherDataSource}'s own private method of the same
     *  name/shape — duplicated here (not extracted into a shared utility) since that class's own
     *  copy is {@code private} and this is the smallest change that doesn't touch working,
     *  already-proven Create-side code. */
    public static ApVoucherExecutionGroup resolveActiveGroup(String xmlGroupParam) {
        String prop = System.getProperty("apv.group");
        if (prop == null || prop.isBlank()) prop = xmlGroupParam;
        if (prop == null || prop.isBlank()) return null;
        try {
            return ApVoucherExecutionGroup.valueOf(prop.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            Log.warn("[DB] Unknown apv.group value '{}' — running all groups.", prop);
            return null;
        }
    }

    /** {@code true} when {@code activeGroup} is {@code null} (no filter) or matches {@code targetGroup}. */
    public static boolean matchesGroup(ApVoucherExecutionGroup activeGroup, ApVoucherExecutionGroup targetGroup) {
        return activeGroup == null || activeGroup == targetGroup;
    }
}
