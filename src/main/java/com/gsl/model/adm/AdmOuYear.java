package com.gsl.model.adm;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDateTime;

/**
 * Maps to admou_year — per-OU (business unit) open/closed status for a fiscal year
 * ({@link AdmYear#getYcode()}). {@code admyearStatus} is {@code "O"} (open) or {@code "C"}
 * (closed) — a document belonging to a closed year cannot be edited, regardless of whether
 * the edited date itself would otherwise be in range.
 *
 * <p>Fetched via {@link com.gsl.database.dao.adm.AdmOuYearDao#findByYcodeAndOu(long, long)};
 * {@code AccountingPeriodResolver#isYearOpen} checks this row first, before the finer-grained
 * month status ({@link AdmOuMonth}).
 */
@Data
public class AdmOuYear {
    private long code;

    @ColumnName("admyear_code")
    private long admyearCode;

    @ColumnName("admou_code")
    private long admouCode;

    @ColumnName("admyear_status")
    private String admyearStatus;

    @ColumnName("admyear_closed_on")
    private LocalDateTime admyearClosedOn;

    // Boxed, not primitive — null (unclosed year) would otherwise NPE unboxing into a bare long.
    @ColumnName("admyear_closed_by")
    private Long admyearClosedBy;
}
