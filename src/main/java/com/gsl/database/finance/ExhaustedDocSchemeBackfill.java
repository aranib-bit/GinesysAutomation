package com.gsl.database.finance;

import com.gsl.api.bulk.DocSchemeFiller;
import com.gsl.api.integration.DocSchemeProvisioner;
import com.gsl.config.FinanceConfig;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.VoucherDomain;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeResolver;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeSelection;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.util.List;

/**
 * Hands the "Save on a full document scheme" scenario a scheme that really has no numbers left, creating
 * one on demand — the data-availability backfill pattern ({@code DataAvailabilityGuard}: try, find nothing,
 * backfill, retry) for doc schemes.
 *
 * <p>Order: (1) any already-full scheme mapped to the voucher menu (a dedicated test scheme is preferred, a
 * genuinely full real scheme is accepted); (2) otherwise provision the dedicated test scheme
 * ({@link DocSchemeProvisioner}: create, tag to the menu, tag to the owner site) and fill it to its cap with
 * k6 parallel users ({@link DocSchemeFiller}); (3) resolve again.
 *
 * <p>Provisioning and filling create real data, so they only run in environments listed in
 * {@code docscheme.provision.allowed.envs} and only ever touch schemes in {@code docscheme.test.names};
 * elsewhere the scenario is skipped rather than failed. A JVM-wide lock keeps parallel test threads from
 * provisioning or filling the same scheme twice.
 */
public final class ExhaustedDocSchemeBackfill {

    private static final Object LOCK = new Object();

    private ExhaustedDocSchemeBackfill() {}

    /**
     * Resolves the scheme for {@code mode}: AUTO/MANUAL go straight to {@link DocSchemeResolver};
     * EXHAUSTED additionally backfills a full scheme when none exists (see class Javadoc).
     */
    public static DocSchemeSelection resolve(Jdbi jdbi, VoucherDomain domain, List<Long> siteCodes,
                                             long ownerSiteCode, DocSchemeMode mode) {
        String shortcode = domain.getMenuShortcode();
        if (mode != DocSchemeMode.EXHAUSTED) {
            return DocSchemeResolver.resolve(jdbi, shortcode, siteCodes, ownerSiteCode, mode);
        }
        try {
            return DocSchemeResolver.resolve(jdbi, shortcode, siteCodes, ownerSiteCode, mode);
        } catch (SkipException noFullScheme) {
            if (!DocSchemeProvisioner.isEnvironmentAllowed()) {
                throw new SkipException(noFullScheme.getMessage()
                        + "\nProvisioning/filling a test scheme is not allowed in this environment "
                        + "(docscheme.provision.allowed.envs).", noFullScheme);
            }
            synchronized (LOCK) {
                try {   // another thread may have filled it while this one waited for the lock
                    return DocSchemeResolver.resolve(jdbi, shortcode, siteCodes, ownerSiteCode, mode);
                } catch (SkipException stillNone) {
                    backfill(domain, siteCodes, ownerSiteCode);
                    return DocSchemeResolver.resolve(jdbi, shortcode, siteCodes, ownerSiteCode, mode);
                }
            }
        }
    }

    private static void backfill(VoucherDomain domain, List<Long> siteCodes, long ownerSiteCode) {
        FinanceConfig cfg = ConfigFactory.getFinanceConfig();
        boolean gv = domain == VoucherDomain.GV;
        String docName = gv ? cfg.docSchemeTestGvName() : cfg.docSchemeTestArApName();
        String prefix = gv ? cfg.docSchemeTestGvPrefix() : cfg.docSchemeTestArApPrefix();
        // The AR/AP test scheme is shared (like the real "AR/AP Voucher"), so it is tagged to both menus.
        List<String> menus = gv
                ? List.of(VoucherDomain.GV.getMenuShortcode())
                : List.of(VoucherDomain.AR.getMenuShortcode(), VoucherDomain.AP.getMenuShortcode());

        ReportLogger.dataBackfillWarn("[ExhaustedDocSchemeBackfill] No full doc scheme for {} — provisioning and "
                + "filling test scheme '{}' (width {})", domain, docName, cfg.docSchemeTestWidth());
        DocSchemeProvisioner.ensureAll(
                List.of(new DocSchemeProvisioner.Spec(docName, prefix, cfg.docSchemeTestWidth(), menus)),
                ownerSiteCode);
        DocSchemeFiller.fillToCap(domain, docName, ownerSiteCode, siteCodes).requireFull();
    }
}
