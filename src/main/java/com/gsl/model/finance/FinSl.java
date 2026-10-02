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

    /** Primary key. */
    private long   slcode;
    private String slid;
    private String slname;

    /** FK → {@code fingl.glcode} — the parent Sundry Debtors/Creditors master GL this SL rolls up to. */
    private long   glcode;     // Parent GL — Sundry Debtors or Sundry Creditors master GL

    /** FK → {@link AdmCls#getClscode()} — this SL's classification type (Customer/Supplier/etc). */
    private long   clscode;    // FK → admcls.clscode (SL classification type)

    /** {@code 1} = system-defined, {@code 0} = user-defined — mirrors {@link AdmCls#getCmpcode()}. */
    private int    cmpcode;    // 1 = system-defined, 0 = user-defined

    /** {@code 'Y'} = TDS applicable for this SL, {@code 'N'} = not applicable. */
    @ColumnName("tds_app")
    private String tdsApplicable;   // 'Y' = TDS applicable, 'N' = not applicable
}
