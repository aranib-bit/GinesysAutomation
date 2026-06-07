package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Maps to finsl — Sub-Ledger master table.
 * Each entry represents a Sundry Debtor / Creditor account under a parent GL (glcode).
 *
 * <p>{@code clscode} links to {@code admcls.clscode} to identify the SL classification type.
 * {@code cmpcode}: {@code 1} = system-defined, {@code 0} = user-defined (mirrors admcls.cmpcode).
 */
@Data
public class FinSl {

    private long   slcode;
    private String slid;
    private String slname;
    private long   glcode;     // Parent GL — Sundry Debtors or Sundry Creditors master GL
    private long   clscode;    // FK → admcls.clscode (SL classification type)
    private int    cmpcode;    // 1 = system-defined, 0 = user-defined

    @ColumnName("tds_app")
    private String tdsApplicable;   // 'Y' = TDS applicable, 'N' = not applicable
}
