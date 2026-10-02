package com.gsl.database.dao.adm;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;

/**
 * DAO for admsite_ou — bridges a {@code finvchmain.admsite_code_owner} value (an
 * {@code admsite.code}) to its {@code admou_code} (the OU that {@code admou_year}/
 * {@code admou_month} are actually keyed on). Live-verified: every General Voucher's
 * {@code admsite_code_owner} maps to exactly one {@code admou_code} for this tenant, so
 * callers can safely take the first result.
 */
public interface AdmSiteOuDao {

    /** Returns the {@code admou_code}(s) the given {@code admsite_code} bridges to; callers (e.g.
     *  {@code AccountingPeriodResolver#resolveOuCode}) take the first result per this class's
     *  live-verified one-to-one guarantee for this tenant. */
    @SqlQuery("SELECT DISTINCT admou_code FROM admsite_ou WHERE admsite_code = :admsiteCode")
    List<Long> findOuCodesByAdmsiteCode(@Bind("admsiteCode") long admsiteCode);
}
