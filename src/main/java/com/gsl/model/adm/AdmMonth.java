package com.gsl.model.adm;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDateTime;

/**
 * Maps to admmonth — one row per calendar month within a fiscal year
 * ({@link AdmYear#getYcode()}), with its own {@code [dtfr, dtto]} date range.
 */
@Data
public class AdmMonth {
    private long mcode;
    private LocalDateTime monstdt;
    private long ycode;

    @ColumnName("month_name")
    private String monthName;
    private LocalDateTime dtfr;
    private LocalDateTime dtto;
}
