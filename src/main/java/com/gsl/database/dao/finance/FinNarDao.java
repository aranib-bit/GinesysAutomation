package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinNar;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

/**
 * DAO for finnar — GL-scoped master list of registered narration texts (see {@link FinNar}).
 * Not a per-voucher table: rows are keyed by (glcode, nartext), with no FK to finvchmain.
 */
@RegisterBeanMapper(FinNar.class)
public interface FinNarDao {

    /**
     * Looks up the registered narration entry for a given GL + narration text pair — used by
     * {@code FinVchMainBaseValidator} to assert that a voucher header's {@code nartext} is a
     * genuinely registered finnar entry for its {@code glcode}, not free text.
     *
     * @return empty when no finnar row is registered for this (glcode, nartext) combination
     */
    @SqlQuery("SELECT narcode, nartext, glcode FROM finnar WHERE glcode = :glcode AND nartext = :nartext")
    Optional<FinNar> findByGlcodeAndNartext(@Bind("glcode") long glcode, @Bind("nartext") String nartext);
}
