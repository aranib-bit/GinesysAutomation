package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinTdsType;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for {@code fintdstype} — TDS Nature master table.
 *
 * <p>Used by {@link com.gsl.database.query.TdsNameQuery} to resolve TDS names
 * during {@code ADVANCE_WITH_TDS} test data generation.
 *
 * <p>All queries filter {@code ext = 'N'} to exclude logically deleted natures.
 */
@RegisterBeanMapper(FinTdsType.class)
public interface FinTdsTypeDao {

    /**
     * Returns all active TDS natures ordered by {@code tdstypecode}.
     *
     * <p>Used when a vendor has <b>no</b> rows in {@code fintds_exception}
     * (i.e. no nature-specific tagging) — all natures are then available as candidates.
     *
     * @return all rows from {@code fintdstype} where {@code ext = 'N'}
     */
    @SqlQuery("SELECT tdstypecode, tdsname, tdstype, ext FROM fintdstype " +
              "WHERE ext = 'N' ORDER BY tdstypecode")
    List<FinTdsType> findAll();

    /**
     * Returns active TDS natures whose {@code tdstypecode} is in the given list.
     *
     * <p>Used when a vendor has rows in {@code fintds_exception} — only those
     * tagged {@code tdstypecode}s are eligible candidates.
     *
     * @param codes list of {@code tdstypecode} values (bigint) from {@code fintds_exception}
     * @return matching rows from {@code fintdstype} where {@code ext = 'N'}
     */
    @SqlQuery("SELECT tdstypecode, tdsname, tdstype, ext FROM fintdstype " +
              "WHERE tdstypecode IN (<codes>) AND ext = 'N' ORDER BY tdstypecode")
    List<FinTdsType> findByCodes(@BindList("codes") List<Long> codes);
}
