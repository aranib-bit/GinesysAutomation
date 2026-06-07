package com.gsl.enums.Finance.Operations.APVoucher;

/**
 * Controls which vendor TDS-nature-tagging situation is targeted during
 * {@code ADVANCE_WITH_TDS} regression fixture generation.
 *
 * <p>Drives two things together:
 * <ol>
 *   <li><b>Vendor selection SQL</b> — a subquery on {@code fintds_exception} narrows
 *       {@code findVendorSlsForAdvance()} to only vendors that match the scenario.</li>
 *   <li><b>TDS name resolution</b> — {@link com.gsl.database.query.TdsNameQuery#resolve}
 *       uses the candidate set that matches the count outcome.</li>
 * </ol>
 *
 * <h3>DB-verified counts (as of plan date)</h3>
 * <ul>
 *   <li>{@link #ANY_NATURE}   — 53 vendors with {@code tds_app='Y'} and no rows in
 *       {@code fintds_exception} → all active {@code fintdstype} rows are candidates.</li>
 *   <li>{@link #SINGLE_NATURE} — multiple vendors with exactly 1 nature tagged
 *       (e.g. AARUCHI FASHIONS, slcode=193, tdstypecode=3).</li>
 *   <li>{@link #MULTI_NATURE_ANY} / {@link #MULTI_NATURE_BY_TYPE} — 0 vendors in current DB;
 *       constants are implemented for future use when data exists.</li>
 * </ul>
 */
public enum TdsNatureMode {

    /**
     * Vendor has {@code tds_app='Y'} but <b>no</b> rows in {@code fintds_exception}.
     * All active natures in {@code fintdstype} (where {@code ext='N'}) are available.
     * TDS name picked by {@code slcode % allNatures.size()}.
     */
    ANY_NATURE,

    /**
     * Vendor has exactly <b>1</b> row in {@code fintds_exception}.
     * Only that tagged nature is available — no ambiguity in selection.
     */
    SINGLE_NATURE,

    /**
     * Vendor has <b>more than 1</b> row in {@code fintds_exception}.
     * All tagged natures are candidates; TDS name picked by {@code slcode % candidates.size()}.
     *
     * <p><em>Note:</em> No vendors with COUNT &gt; 1 exist in the current DB.
     * Activate the corresponding scenario type when data is available.
     */
    MULTI_NATURE_ANY,

    /**
     * Vendor has <b>more than 1</b> row in {@code fintds_exception}, and at least one of the
     * tagged natures matches the {@link com.gsl.utils.dataprovider.fixture.finance.ApVoucherFixtureOptions#getPreferredTdsType()}
     * ({@code 'S'} = Services, {@code 'G'} = Goods).
     * Filtered candidates are used; falls back to all tagged natures if no type match.
     *
     * <p><em>Note:</em> No vendors with COUNT &gt; 1 exist in the current DB.
     * Activate the corresponding scenario type when data is available.
     */
    MULTI_NATURE_BY_TYPE
}
