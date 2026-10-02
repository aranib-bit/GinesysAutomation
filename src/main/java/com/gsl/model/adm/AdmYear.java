package com.gsl.model.adm;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Maps to admyear — the fiscal year calendar, one row per fiscal year with its {@code [dtfr,
 * dtto]} date range. Fetched via {@link com.gsl.database.dao.adm.AdmYearDao}; {@code
 * AccountingPeriodResolver#findCurrentYear} scans {@link com.gsl.database.dao.adm.AdmYearDao#findAll()}
 * to resolve which fiscal year contains a given date, since {@code admyear} is small, tenant-wide
 * master data rather than per-document.
 *
 * <p>See also {@link AdmMonth} (calendar months within a year) and {@link AdmOuYear} (per-OU
 * open/closed status for a year).
 */
@Data
public class AdmYear {
    /** Primary key — the fiscal year code referenced by {@link AdmMonth#getYcode()} and
     *  {@link AdmOuYear#getAdmyearCode()}. */
    private long ycode;
    private String yname;
    private LocalDateTime dtfr;
    private LocalDateTime dtto;
}
