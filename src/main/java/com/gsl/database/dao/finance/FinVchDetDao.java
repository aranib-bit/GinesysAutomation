package com.gsl.database.dao.finance;

import com.gsl.model.finance.FinVchDet;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.math.BigDecimal;
import java.util.List;

/**
 * DAO for finvchdet — Finance module.
 */
@RegisterBeanMapper(FinVchDet.class)
public interface FinVchDetDao {

    @SqlQuery("SELECT * FROM finvchdet WHERE vchcode = :vchcode")
    List<FinVchDet> findByVoucherCode(@Bind("vchcode") long vchcode);

    @SqlQuery("SELECT COUNT(*) FROM finvchdet WHERE vchcode = :vchcode")
    int countByVoucherCode(@Bind("vchcode") long vchcode);

    @SqlQuery("SELECT COALESCE(SUM(damount), 0) FROM finvchdet WHERE vchcode = :vchcode")
    BigDecimal sumDebitByVoucherCode(@Bind("vchcode") long vchcode);

    @SqlQuery("SELECT COALESCE(SUM(camount), 0) FROM finvchdet WHERE vchcode = :vchcode")
    BigDecimal sumCreditByVoucherCode(@Bind("vchcode") long vchcode);

    @SqlQuery("SELECT * FROM finvchdet WHERE vchcode = :vchcode AND slcode = :slcode")
    List<FinVchDet> findByVoucherCodeAndSlCode(
            @Bind("vchcode") long vchcode,
            @Bind("slcode") long slcode);

    /**
     * Uniquely identifies a single finvchdet row using the three-column key:
     *   vchcode  — from finvchmain where scheme_docno = voucher number
     *   slcode   — from finsl where slname = SubLedger name (+ slid when provided)
     *   glcode   — from fingl where glname = General Ledger name
     */
    @SqlQuery("SELECT * FROM finvchdet WHERE vchcode = :vchcode AND slcode = :slcode AND glcode = :glcode")
    FinVchDet findByVchcodeSlcodeGlcode(
            @Bind("vchcode") long vchcode,
            @Bind("slcode") long slcode,
            @Bind("glcode") long glcode);
}
