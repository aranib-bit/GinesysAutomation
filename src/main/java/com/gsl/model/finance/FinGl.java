package com.gsl.model.finance;

import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Maps to fingl — General Ledger master table.
 * srctype = 'C' → Cash ledger (payment_mode stored as 'H')
 * srctype = 'B' → Bank ledger (payment_mode stored as 'C' or 'R')
 */
@Data
public class FinGl {

    private long   glcode;
    private String glname;

    @ColumnName("srctype")
    private String srctype;   // 'C' = Cash, 'B' = Bank
}
