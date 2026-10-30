package com.gsl.database.finance;

import com.gsl.api.client.MasterDataSupport;
import com.gsl.api.client.TdsMasterApiClient;
import com.gsl.service.TdsMasterService;
import com.gsl.api.client.ApVoucherApiClient;
import com.gsl.api.client.VendorApiClient;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.common.SubLedgerLookupResult;
import com.gsl.database.BackfillStrategy;
import com.gsl.enums.ApiPlatform;
import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.APVoucher.TdsNatureMode;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.dataprovider.source.finance.ApiVoucherSupport;
import org.testng.SkipException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AP Advance-with-TDS shortfall backfill: when no TDS-applicable party exists (or none with the TDS-nature shape the scenario needs), it
 * creates a new Vendor through {@code POST VendorMaster/Create} (HAR: CreateVendor*), shaped by the scenario's {@link TdsNatureMode}:
 * <ul>
 *   <li>{@code null} / {@code ANY_NATURE}: TDS applicable, no nature tagged;</li>
 *   <li>{@code SINGLE_NATURE}: one nature;</li>
 *   <li>{@code MULTI_NATURE_ANY}: three natures (or as many as are configured, at least two);</li>
 *   <li>{@code MULTI_NATURE_BY_TYPE}: several natures including one of the scenario's preferred Services/Goods type.</li>
 * </ul>
 * Only a NEW record is created - existing parties are never edited. The environment codes (class, ledger, trade group, ...) are copied from
 * an existing supplier, the natures and OU come from the Shared lookups, so nothing is environment-hardcoded. The backfill returns once the
 * Finance sub-ledger lookup (what the selector reads) shows the new vendor as TDS-applicable.
 */
public final class TdsVendorBackfillStrategy implements BackfillStrategy {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** The sub-ledger lookup can lag a few seconds behind a master create; poll this long before giving up. */
    private static final long VISIBLE_WAIT_MS = 30_000L;
    private static final long VISIBLE_POLL_MS = 1_500L;

    private final TdsNatureMode mode;
    private final String preferredTdsType;

    /** @param mode             the scenario's nature mode, or null for a plain TDS-applicable vendor
     *  @param preferredTdsType {@code 'S'} (Services) or {@code 'G'} (Goods) for {@code MULTI_NATURE_BY_TYPE}; ignored otherwise */
    public TdsVendorBackfillStrategy(TdsNatureMode mode, String preferredTdsType) {
        this.mode = mode;
        this.preferredTdsType = preferredTdsType;
    }

    @Override
    public void backfill(ApiPlatform platform, int neededQuantity) {
        for (int i = 0; i < neededQuantity; i++) {
            final int idx = i;
            ApiSessionFactory.withWebApiSession(session -> {
                createOne(session, idx);
                return null;
            });
        }
    }

    private void createOne(ApiSessionBundle session, int idx) {
        VendorApiClient vendors = session.getVendorApiClient();
        ApVoucherApiClient ap = session.getApVoucherApiClient();
        ApiVoucherSupport.Owner owner = ApiVoucherSupport.resolveOwner(session, CashBankType.BANK);

        VendorApiClient.EnvCodes env = vendors.fetchEnvCodesFromExistingVendor(owner.siteCode(), owner.ouCode(), owner.workDate());
        if (env == null) {
            throw new SkipException("Cannot backfill a TDS vendor: the vendor master has no existing supplier to copy the environment codes from.");
        }
        // The TDS masters a vendor depends on, created only when the environment lacks them: source, then (for the nature modes) agency and sections.
        TdsMasterService tdsMasters = new TdsMasterService(session.getTdsMasterApiClient(),
                new TdsMasterApiClient.Context(owner.siteCode(), owner.ouCode(), owner.empCode(), owner.workDate()));
        tdsMasters.ensureSource();
        Long polledSource = MasterDataSupport.pollFor(VISIBLE_WAIT_MS, VISIBLE_POLL_MS, () -> vendors.fetchFirstTdsSourceCode().isPresent()
                ? Long.valueOf(vendors.fetchFirstTdsSourceCode().getAsLong()) : null);
        if (polledSource == null) {
            throw new SkipException("Cannot backfill a TDS vendor: a TDS source exists but Shared/GetTDSSourceLookup does not offer one within "
                    + VISIBLE_WAIT_MS / 1000 + "s.");
        }
        long sourceCode = polledSource;
        VendorApiClient.OuRef ou = vendors.fetchOrganizationUnits().stream()
                .filter(o -> o.code() == owner.ouCode()).findFirst()
                .orElseThrow(() -> new SkipException("Cannot backfill a TDS vendor: OU " + owner.ouCode() + " is not in the OU lookup."));
        int minimumNatures = minimumNatures();
        if (minimumNatures > 0) {
            int created = tdsMasters.ensureSections(sourceCode, minimumNatures, preferredTdsType);
            if (created > 0) {
                MasterDataSupport.pollFor(VISIBLE_WAIT_MS, VISIBLE_POLL_MS, () -> vendors.fetchTdsNatures().size() >= minimumNatures ? Boolean.TRUE : null);
            }
        }
        List<VendorApiClient.NatureSpec> natures = pickNatures(vendors.fetchTdsNatures(), ou);

        String tag = mode == null ? "PLAIN" : mode.name();
        String stamp = LocalDateTime.now().format(STAMP);
        String slId = "ZT" + (System.currentTimeMillis() % 1_000_000L) + idx;
        VendorApiClient.VendorSpec spec = new VendorApiClient.VendorSpec("ZZ API TDS " + tag + " " + stamp + "-" + idx, slId, true, natures);

        ReportLogger.dataBackfill("[TdsVendorBackfillStrategy] Creating vendor '{}' (slid={}, mode={}, natures={})",
                spec.name(), slId, tag, natures.stream().map(VendorApiClient.NatureSpec::tdsTypeName).toList());
        vendors.createVendor(spec, env, sourceCode, owner.siteCode(), owner.ouCode(), owner.workDate());

        long deadline = System.currentTimeMillis() + VISIBLE_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<SubLedgerLookupResult> sls = ap.fetchSubLedgers(owner.siteCode(), owner.ouCode(), (int) owner.empCode(), owner.workDate());
            if (sls.stream().anyMatch(s -> slId.equals(s.getSlid()) && s.isTdsApplicable())) {
                ReportLogger.dataBackfill("[TdsVendorBackfillStrategy] Vendor '{}' is visible in the sub-ledger lookup as TDS-applicable.", spec.name());
                return;
            }
            try {
                Thread.sleep(VISIBLE_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        ReportLogger.dataBackfillWarn("[TdsVendorBackfillStrategy] Vendor '{}' was created but is not yet TDS-applicable in the sub-ledger lookup.", spec.name());
    }

    /** How many TDS natures must exist in the environment for the scenario's mode. */
    private int minimumNatures() {
        // A vendor with no nature tagged is offered every nature with a rate for its source, so even the plain mode needs one to exist.
        if (mode == null || mode == TdsNatureMode.ANY_NATURE || mode == TdsNatureMode.SINGLE_NATURE) return 1;
        return 2;
    }

    /** Natures for the scenario's mode; throws a data skip when the environment has too few natures configured for it. */
    private List<VendorApiClient.NatureSpec> pickNatures(List<VendorApiClient.TdsNatureRef> all, VendorApiClient.OuRef ou) {
        List<VendorApiClient.NatureSpec> out = new ArrayList<>();
        if (mode == null || mode == TdsNatureMode.ANY_NATURE) return out;
        int want = switch (mode) {
            case SINGLE_NATURE -> 1;
            case MULTI_NATURE_ANY, MULTI_NATURE_BY_TYPE -> Math.min(3, all.size());
            default -> 0;
        };
        List<VendorApiClient.TdsNatureRef> ordered = new ArrayList<>(all);
        if (mode == TdsNatureMode.MULTI_NATURE_BY_TYPE) {
            // preferred type first, so it is always among the picked natures
            ordered.sort((a, b) -> Boolean.compare(!matchesPreferred(a), !matchesPreferred(b)));
        }
        for (int i = 0; i < want && i < ordered.size(); i++) {
            VendorApiClient.TdsNatureRef n = ordered.get(i);
            out.add(VendorApiClient.NatureSpec.plain(n.code(), n.name(), ou.code(), ou.name()));
        }
        int minimum = mode == TdsNatureMode.SINGLE_NATURE ? 1 : 2;
        if (out.size() < minimum) {
            throw new SkipException("Cannot backfill a TDS vendor for nature mode " + mode + ": only " + all.size() + " TDS nature(s) are configured.");
        }
        if (mode == TdsNatureMode.MULTI_NATURE_BY_TYPE && out.stream().noneMatch(s -> all.stream()
                .anyMatch(n -> n.code() == s.tdsTypeCode() && matchesPreferred(n)))) {
            throw new SkipException("Cannot backfill a TDS vendor for nature mode " + mode + ": no configured TDS nature of type '" + preferredTdsType + "'.");
        }
        return out;
    }

    private boolean matchesPreferred(VendorApiClient.TdsNatureRef n) {
        return preferredTdsType != null && n.selectionType() != null
                && n.selectionType().toUpperCase().startsWith(preferredTdsType.toUpperCase());
    }

    /** {@inheritDoc} No k6 script exists for this path; quantities are always small, so K6BULK is never chosen. */
    @Override
    public String k6ScriptKey() {
        return "tdsVendorBackfill";
    }
}
