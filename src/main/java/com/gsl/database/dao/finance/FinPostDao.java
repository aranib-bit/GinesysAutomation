package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinPost;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * DAO for finpost — Finance module.
 *
 * SHARED TABLE: finpost is used by Finance and Procurement.
 * All queries scoped by entno or postcode to prevent cross-module reads.
 */
@RegisterBeanMapper(FinPost.class)
public interface FinPostDao {

    /** Returns all finpost rows for the given {@code entcode} (voucher/invoice entry code). */
    @SqlQuery("SELECT * FROM finpost WHERE entcode = :entcode")
    List<FinPost> findByEntCode(@Bind("entcode") String entcode);

    /** Looks up a single finpost row by its own primary key. */
    @SqlQuery("SELECT * FROM finpost WHERE postcode = :postcode")
    Optional<FinPost> findByPostCode(@Bind("postcode") long postcode);

    /**
     * Resolves the human-readable document number for a posting row regardless of entity type
     * (voucher, purchase invoice, journal...) — {@code fintag.entcode} is not always a
     * {@code finvchmain.vchcode}, but its {@code finpost} row always carries {@code scheme_docno}.
     */
    @SqlQuery("SELECT scheme_docno FROM finpost WHERE postcode = :postcode")
    Optional<String> findSchemeDocnoByPostCode(@Bind("postcode") long postcode);

    /** Returns all finpost rows for the given {@code entno} (voucher number / invoice number). */
    @SqlQuery("SELECT * FROM finpost WHERE entno = :entno")
    List<FinPost> findByEntNo(@Bind("entno") String entno);

    /** Returns the number of finpost rows for the given {@code entcode}. */
    @SqlQuery("SELECT COUNT(*) FROM finpost WHERE entcode = :entcode")
    int countByEntCode(@Bind("entcode") String entcode);

    /** Sums {@code damount} across all finpost rows for the given {@code entcode}; {@code 0} when none exist. */
    @SqlQuery("SELECT COALESCE(SUM(damount), 0) FROM finpost WHERE entcode = :entcode")
    BigDecimal sumDebitByEntCode(@Bind("entcode") String entcode);

    /** Sums {@code camount} across all finpost rows for the given {@code entcode}; {@code 0} when none exist. */
    @SqlQuery("SELECT COALESCE(SUM(camount), 0) FROM finpost WHERE entcode = :entcode")
    BigDecimal sumCreditByEntCode(@Bind("entcode") String entcode);
}
