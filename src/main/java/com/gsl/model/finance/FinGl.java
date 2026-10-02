package com.gsl.model.finance;

import com.gsl.enums.GlAccountType;
import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

/**
 * Maps to fingl — General Ledger master table.
 * srctype = 'C' → Cash ledger (payment_mode stored as 'H')
 * srctype = 'B' → Bank ledger (payment_mode stored as 'C' or 'R')
 *
 * <p>slapp/costapp drive General Voucher's per-line GL behavior type
 * (see com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType):
 * <ul>
 *     <li>slapp = 'Y' → Sub Ledger Applicable</li>
 *     <li>costapp = 'Y' → Site Applicable (a.k.a. Cost Center Applicable)</li>
 * </ul>
 *
 * <p>{@code type} is the GL's accounting classification (Assets/Liability/Income/Expense,
 * stored as single-letter codes 'A'/'L'/'I'/'E' — see {@link GlAccountType}), used by the
 * General Voucher Expense/Income Detail combination matrix.
 */
@Data
public class FinGl {

    private long   glcode;
    private String glname;

    @ColumnName("srctype")
    private String srctype;   // 'C' = Cash, 'B' = Bank

    @ColumnName("slapp")
    private String slapp;     // 'Y'/'N' — Sub Ledger Applicable

    @ColumnName("costapp")
    private String costapp;   // 'Y'/'N' — Site (Cost Center) Applicable

    @ColumnName("type")
    private String type;      // 'A'/'L'/'I'/'E' — Assets/Liability/Income/Expense

    /** True when {@link #slapp} = {@code "Y"} (Sub Ledger Applicable). */
    public boolean isSubLedgerApplicable() {
        return "Y".equalsIgnoreCase(slapp);
    }

    /** True when {@link #costapp} = {@code "Y"} (Site / Cost Center Applicable). */
    public boolean isSiteApplicable() {
        return "Y".equalsIgnoreCase(costapp);
    }

    /** Resolves {@link #type} into a {@link GlAccountType}. Throws if {@code type} is unset/unrecognized. */
    public GlAccountType accountType() {
        return GlAccountType.fromCode(type);
    }
}
