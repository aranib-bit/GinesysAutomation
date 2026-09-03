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

    // ORDER BY f.code (Round 9, RC-10): finvchdet has no line-sequence column at all, so without
    // an explicit order Postgres's fetch order is undefined and needn't match the app's actual
    // grid row order. DbGeneralVoucherEditDataSource fabricates LineNo purely from this fetch's
    // position, then maps LineNo back to a DOM grid-row index — so an unordered fetch here
    // silently causes the edit to land on the wrong row. Verified live against the exact failing
    // voucher (VG/00375/26-27, vchcode 562638): ORDER BY code ASC reproduces the app's real grid
    // order (glcode=33 'Interest on Loan CC. A/c.' first), confirmed against the live DOM.
    @SqlQuery("SELECT f.*, a.name AS ref_admsite_name " +
              "FROM finvchdet f " +
              "LEFT JOIN admsite a ON a.code = f.ref_admsite_code " +
              "WHERE f.vchcode = :vchcode " +
              "ORDER BY f.code")
    List<FinVchDet> findByVoucherCode(@Bind("vchcode") long vchcode);

    @SqlQuery("SELECT COUNT(*) FROM finvchdet WHERE vchcode = :vchcode")
    int countByVoucherCode(@Bind("vchcode") long vchcode);

    @SqlQuery("SELECT COALESCE(SUM(damount), 0) FROM finvchdet WHERE vchcode = :vchcode")
    BigDecimal sumDebitByVoucherCode(@Bind("vchcode") long vchcode);

    @SqlQuery("SELECT COALESCE(SUM(camount), 0) FROM finvchdet WHERE vchcode = :vchcode")
    BigDecimal sumCreditByVoucherCode(@Bind("vchcode") long vchcode);

    @SqlQuery("SELECT f.*, a.name AS ref_admsite_name " +
              "FROM finvchdet f " +
              "LEFT JOIN admsite a ON a.code = f.ref_admsite_code " +
              "WHERE f.vchcode = :vchcode AND f.slcode = :slcode")
    List<FinVchDet> findByVoucherCodeAndSlCode(
            @Bind("vchcode") long vchcode,
            @Bind("slcode") long slcode);

    /**
     * Uniquely identifies a single finvchdet row using the three-column key:
     *   vchcode  — from finvchmain where scheme_docno = voucher number
     *   slcode   — from finsl where slname = SubLedger name (+ slid when provided)
     *   glcode   — from fingl where glname = General Ledger name
     */
    @SqlQuery("SELECT f.*, a.name AS ref_admsite_name " +
              "FROM finvchdet f " +
              "LEFT JOIN admsite a ON a.code = f.ref_admsite_code " +
              "WHERE f.vchcode = :vchcode AND f.slcode = :slcode AND f.glcode = :glcode")
    FinVchDet findByVchcodeSlcodeGlcode(
            @Bind("vchcode") long vchcode,
            @Bind("slcode") long slcode,
            @Bind("glcode") long glcode);
}
