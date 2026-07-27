package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to finvchmain — AR Voucher Header table.
 * Finance module only (not shared with other modules).
 */
@Data
public class FinVchMain {

    private long vchcode;

    private String vchno;

    @ColumnName("scheme_docno")
    private String schemeDocno;
    private LocalDate vchdt;
    private String vchtype;
    private String refno;

    /** Voucher narration / remarks. Maps to finvchmain.nartext (varchar 500). */
    private String nartext;
    private long glcode;
    private BigDecimal damount;
    private BigDecimal camount;

    @ColumnName("admsite_code_owner")
    private long admsiteCodeOwner;
    private long ycode;

    @ColumnName("entry_source")
    private String entrySource;      // WEB or API

    private String status;

    @ColumnName("release_status")
    private String releaseStatus;

    @ColumnName("creation_dt")
    private LocalDateTime creationDt;
}
