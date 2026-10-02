package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinTag;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.math.BigDecimal;
import java.util.List;

/**
 * DAO for fintag — Finance module.
 *
 * SHARED TABLE: fintag is used by Finance and Procurement.
 * All queries scoped to specific postcode1/postcode2 values.
 */
@RegisterBeanMapper(FinTag.class)
public interface FinTagDao {

    /** Returns all fintag rows tagging the given voucher-side finpost entry ({@code postcode1}). */
    @SqlQuery("SELECT * FROM fintag WHERE postcode1 = :postcode1")
    List<FinTag> findByPostCode1(@Bind("postcode1") long postcode1);

    /** Returns the number of fintag rows tagging the given voucher-side finpost entry ({@code postcode1}). */
    @SqlQuery("SELECT COUNT(*) FROM fintag WHERE postcode1 = :postcode1")
    int countByPostCode1(@Bind("postcode1") long postcode1);

    /** Sums {@code amount} across all fintag rows tagging the given voucher-side finpost entry; {@code 0} when none exist. */
    @SqlQuery("SELECT COALESCE(SUM(amount), 0) FROM fintag WHERE postcode1 = :postcode1")
    BigDecimal sumTaggedAmountByPostCode1(@Bind("postcode1") long postcode1);

    /** Returns all fintag rows tagging the given voucher-side finpost entry by its {@code entcode1}. */
    @SqlQuery("SELECT * FROM fintag WHERE entcode1 = :entcode1")
    List<FinTag> findByEntCode1(@Bind("entcode1") long entcode1);

    /** Returns the number of fintag rows tagging the given voucher-side finpost entry by its {@code entcode1}. */
    @SqlQuery("SELECT COUNT(*) FROM fintag WHERE entcode1 = :entcode1")
    int countByEntCode1(@Bind("entcode1") long entcode1);

    /** Sums {@code amount} across all fintag rows tagging the given voucher-side finpost entry by its {@code entcode1}; {@code 0} when none exist. */
    @SqlQuery("SELECT COALESCE(SUM(amount), 0) FROM fintag WHERE entcode1 = :entcode1")
    BigDecimal sumTaggedAmountByEntCode1(@Bind("entcode1") long entcode1);

    /** Returns all fintag rows tagging the given invoice-side finpost entry ({@code postcode2}). */
    @SqlQuery("SELECT * FROM fintag WHERE postcode2 = :postcode2")
    List<FinTag> findByPostCode2(@Bind("postcode2") long postcode2);

    /**
     * Returns all fintag rows tagging the given invoice-side finpost entry by its {@code entcode2}
     * — the symmetric counterpart to {@link #findByEntCode1(long)}, added for the AP/AR/GV
     * Document Adjustment "untag" flow (see {@code DocumentAdjustmentFlow.untagIfNeeded}):
     * detecting whether a document under test is tagged requires checking BOTH sides of the
     * denormalized {@code entcode1}/{@code entcode2} pair (= {@code finpost.entcode} =
     * {@code finvchmain.vchcode} — see {@code FinTag}'s field Javadocs), since a document can
     * equally be the "voucher side" (entcode1) or the "invoice side" (entcode2) of a live tag row.
     */
    @SqlQuery("SELECT * FROM fintag WHERE entcode2 = :entcode2")
    List<FinTag> findByEntCode2(@Bind("entcode2") long entcode2);

    /** Returns the fintag row(s) linking a specific voucher-side/invoice-side finpost pair. */
    @SqlQuery("SELECT * FROM fintag WHERE postcode1 = :postcode1 AND postcode2 = :postcode2")
    List<FinTag> findByPostCode1AndPostCode2(
            @Bind("postcode1") long postcode1,
            @Bind("postcode2") long postcode2);
}
