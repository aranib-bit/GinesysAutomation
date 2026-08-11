package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Maps to finvchdet — AR Voucher Detail / Line Item table.
 * Finance module only. One finvchmain → N finvchdet rows.
 */
@Data
public class FinVchDet {

    private long vchcode;       // FK → finvchmain.vchcode
    private long slcode;
    private long glcode;
    private String refno;
    private BigDecimal damount;
    private BigDecimal camount;
    private String chqno;
    private LocalDateTime chqdt;   // PostgreSQL TIMESTAMP → e.g. 2026-04-09 00:00:00.000

    @ColumnName("payment_mode")
    private String paymentMode;

    @ColumnName("tds_app")
    private String tdsApp;

    private String nartext;

    @ColumnName("ref_admsite_name")
    private String refAdmsiteName;

    /**
     * Site-applicability flag denormalized straight onto the detail row —
     * confirmed live (cross-tenant, see FinCostTagValidator) to mirror
     * fingl.costapp for this line at posting time. 'Y' for Type2/Type3
     * General Voucher rows, 'N' for Type1.
     */
    @ColumnName("gl_cc_appl")
    private String glCcAppl;

    @ColumnName("isreversal")
    private String isReversal;
}
