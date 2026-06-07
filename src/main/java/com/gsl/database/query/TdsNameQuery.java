package com.gsl.database.query;

import com.gsl.database.dao.finance.FinTdsExceptionDao;
import com.gsl.database.dao.finance.FinTdsTypeDao;
import com.gsl.model.finance.FinTdsType;
import com.gsl.model.finance.TdsResolution;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves the available TDS name (and type) for a vendor SL from the DB.
 *
 * <h3>Two-path logic</h3>
 * <ol>
 *   <li><b>No nature tagged</b> ({@code COUNT(fintds_exception WHERE slcode) = 0}):
 *       all active rows in {@code fintdstype} are candidates.</li>
 *   <li><b>Nature(s) tagged</b> ({@code COUNT >= 1}):
 *       only the {@code tdstypecode}s present in {@code fintds_exception} for that
 *       {@code slcode} are candidates. Optionally filtered further by
 *       {@code preferredTdsType} ('S'/'G') for multi-nature scenarios.</li>
 * </ol>
 *
 * <h3>Variety rule</h3>
 * The winning candidate is picked at index {@code (int)(slcode % candidates.size())}.
 * This is deterministic per vendor (same vendor always picks the same TDS name) but
 * varies across vendors with different {@code slcode} values — avoiding always picking
 * index 0 when multiple candidates exist.
 *
 * <h3>DB facts (verified)</h3>
 * <ul>
 *   <li>AARUCHI FASHIONS (slcode=193): 1 row in {@code fintds_exception}
 *       (tdstypecode=3) → "TDS ON TECHNICAL SERVICE", tdstype='S'</li>
 *   <li>53 vendors with {@code tds_app='Y'} have no rows in {@code fintds_exception}</li>
 * </ul>
 */
public class TdsNameQuery {

    private TdsNameQuery() {}

    /**
     * Resolves the TDS name and type for the given vendor SL.
     *
     * @param jdbi             active Jdbi instance
     * @param slcode           {@code finsl.slcode} of the vendor
     * @param preferredTdsType {@code 'S'} or {@code 'G'} to prefer when multiple natures
     *                         are tagged ({@link com.gsl.enums.Finance.Operations.APVoucher.TdsNatureMode#MULTI_NATURE_BY_TYPE});
     *                         {@code null} = no type filter
     * @return {@link TdsResolution} with {@code tdsName} (never null) and {@code tdsType}
     * @throws IllegalStateException if no active TDS natures can be found for this vendor
     */
    public static TdsResolution resolve(Jdbi jdbi, long slcode, String preferredTdsType) {
        FinTdsExceptionDao excDao  = jdbi.onDemand(FinTdsExceptionDao.class);
        FinTdsTypeDao      typeDao = jdbi.onDemand(FinTdsTypeDao.class);

        int count = excDao.countBySlcode(slcode);

        List<FinTdsType> candidates;
        if (count == 0) {
            // No natures tagged — all active fintdstype rows are available
            candidates = typeDao.findAll();
            if (candidates.isEmpty())
                throw new IllegalStateException(
                        "[TdsNameQuery] fintdstype has no active (ext='N') entries — " +
                        "cannot resolve TDS name for slcode=" + slcode);
        } else {
            // Vendor has specific natures tagged — only those are selectable
            List<Long> codes = excDao.findTdstypecodesBySlcode(slcode);
            candidates = typeDao.findByCodes(codes);
            if (candidates.isEmpty())
                throw new IllegalStateException(
                        "[TdsNameQuery] fintds_exception has " + count + " row(s) for slcode=" + slcode +
                        " but no active fintdstype found for codes: " + codes +
                        " — check ext='N' on fintdstype rows");

            // Optional: filter by preferred tdstype ('S'=Services, 'G'=Goods)
            if (preferredTdsType != null) {
                List<FinTdsType> filtered = candidates.stream()
                        .filter(t -> preferredTdsType.equalsIgnoreCase(t.getTdstype()))
                        .collect(Collectors.toList());
                // Fall back to all tagged natures if none match the preferred type
                if (!filtered.isEmpty()) {
                    candidates = filtered;
                }
            }
        }

        // Vary pick by slcode: same vendor always gets same name, but different vendors differ
        FinTdsType picked = candidates.get((int)(slcode % candidates.size()));
        return new TdsResolution(picked.getTdsname(), picked.getTdstype());
    }
}
