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

    @SqlQuery("SELECT * FROM fintag WHERE postcode1 = :postcode1")
    List<FinTag> findByPostCode1(@Bind("postcode1") long postcode1);

    @SqlQuery("SELECT COUNT(*) FROM fintag WHERE postcode1 = :postcode1")
    int countByPostCode1(@Bind("postcode1") long postcode1);

    @SqlQuery("SELECT COALESCE(SUM(amount), 0) FROM fintag WHERE postcode1 = :postcode1")
    BigDecimal sumTaggedAmountByPostCode1(@Bind("postcode1") long postcode1);

    @SqlQuery("SELECT * FROM fintag WHERE entcode1 = :entcode1")
    List<FinTag> findByEntCode1(@Bind("entcode1") long entcode1);

    @SqlQuery("SELECT COUNT(*) FROM fintag WHERE entcode1 = :entcode1")
    int countByEntCode1(@Bind("entcode1") long entcode1);

    @SqlQuery("SELECT COALESCE(SUM(amount), 0) FROM fintag WHERE entcode1 = :entcode1")
    BigDecimal sumTaggedAmountByEntCode1(@Bind("entcode1") long entcode1);

    @SqlQuery("SELECT * FROM fintag WHERE postcode2 = :postcode2")
    List<FinTag> findByPostCode2(@Bind("postcode2") long postcode2);

    @SqlQuery("SELECT * FROM fintag WHERE postcode1 = :postcode1 AND postcode2 = :postcode2")
    List<FinTag> findByPostCode1AndPostCode2(
            @Bind("postcode1") long postcode1,
            @Bind("postcode2") long postcode2);
}
