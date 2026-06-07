package com.gsl.database.dao.finance;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for {@code fintds_exception} — Vendor TDS Nature assignment table.
 *
 * <p>Each row represents a TDS nature tagged to a vendor sub-ledger ({@code slcode}).
 * When rows exist for a given {@code slcode}, only those natures are selectable
 * for that vendor in the TDS Name popup. When no rows exist, all active natures
 * in {@code fintdstype} are available.
 *
 * <p>Used by {@link com.gsl.database.query.TdsNameQuery} to implement the
 * two-path TDS name resolution logic.
 */
public interface FinTdsExceptionDao {

    /**
     * Returns the number of TDS natures tagged to the given vendor SL.
     *
     * <ul>
     *   <li>{@code 0}  → no natures tagged; all {@code fintdstype} rows are candidates</li>
     *   <li>{@code 1}  → exactly one nature tagged</li>
     *   <li>{@code >1} → multiple natures tagged (filter by {@code tdstype} if needed)</li>
     * </ul>
     *
     * @param slcode {@code finsl.slcode} of the vendor
     * @return count of rows in {@code fintds_exception} for this slcode
     */
    @SqlQuery("SELECT COUNT(*) FROM fintds_exception WHERE slcode = :slcode")
    int countBySlcode(@Bind("slcode") long slcode);

    /**
     * Returns the {@code tdstypecode} values of all natures tagged to the given vendor SL.
     * Called only when {@link #countBySlcode(long)} returns {@code > 0}.
     *
     * @param slcode {@code finsl.slcode} of the vendor
     * @return list of {@code tdstypecode} values (bigint) for lookup in {@code fintdstype}
     */
    @SqlQuery("SELECT tdstypecode FROM fintds_exception WHERE slcode = :slcode")
    List<Long> findTdstypecodesBySlcode(@Bind("slcode") long slcode);
}
