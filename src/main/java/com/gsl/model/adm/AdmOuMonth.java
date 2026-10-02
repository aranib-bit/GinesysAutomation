package com.gsl.model.adm;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Maps to admou_month — per-OU (business unit) open/closed status for a single calendar month
 * ({@link AdmMonth#getMcode()}), split by domain: {@code finMonthStatus} (Finance — General/AP/AR
 * Voucher), {@code invMonthStatus} (Inventory — e.g. Receipts), {@code cogsRunStatus}. See
 * {@link com.gsl.enums.PeriodDomain} for which status column a given voucher domain checks.
 *
 * <p>Fetched via {@link com.gsl.database.dao.adm.AdmOuMonthDao#findByMonthAndOu(long, long)},
 * after {@link AdmOuYear} confirms the containing fiscal year is open for that OU.
 */
@Data
public class AdmOuMonth {
    private long code;

    @ColumnName("admmonth_code")
    private long admmonthCode;

    @ColumnName("admou_code")
    private long admouCode;

    @ColumnName("cogs_run_status")
    private String cogsRunStatus;

    @ColumnName("inv_month_status")
    private String invMonthStatus;

    @ColumnName("fin_month_status")
    private String finMonthStatus;
}
