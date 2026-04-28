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

    @SqlQuery("SELECT * FROM finpost WHERE entcode = :entcode")
    List<FinPost> findByEntCode(@Bind("entcode") String entcode);

    @SqlQuery("SELECT * FROM finpost WHERE postcode = :postcode")
    Optional<FinPost> findByPostCode(@Bind("postcode") long postcode);

    @SqlQuery("SELECT * FROM finpost WHERE entno = :entno")
    List<FinPost> findByEntNo(@Bind("entno") String entno);

    @SqlQuery("SELECT COUNT(*) FROM finpost WHERE entcode = :entcode")
    int countByEntCode(@Bind("entcode") String entcode);

    @SqlQuery("SELECT COALESCE(SUM(damount), 0) FROM finpost WHERE entcode = :entcode")
    BigDecimal sumDebitByEntCode(@Bind("entcode") String entcode);

    @SqlQuery("SELECT COALESCE(SUM(camount), 0) FROM finpost WHERE entcode = :entcode")
    BigDecimal sumCreditByEntCode(@Bind("entcode") String entcode);
}
