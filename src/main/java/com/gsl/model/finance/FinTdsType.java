package com.gsl.model.finance;

import lombok.Data;

/**
 * Maps to {@code fintdstype} — TDS Nature master table.
 *
 * <p>Each row represents one TDS nature (deduction category) configured in the application.
 * Used by {@link com.gsl.database.query.TdsNameQuery} to resolve the TDS name and type
 * for a vendor SL during {@code ADVANCE_WITH_TDS} test data generation.
 *
 * <h3>Key columns</h3>
 * <ul>
 *   <li>{@code tdstypecode} — PK; referenced by {@code fintds_exception.tdstypecode}</li>
 *   <li>{@code tdsname}     — displayed in the TDS Name popup in the UI</li>
 *   <li>{@code tdstype}     — {@code 'S'} = Services, {@code 'G'} = Goods;
 *                             required by other transactions (carried in
 *                             {@link com.gsl.model.finance.TdsResolution})</li>
 *   <li>{@code ext}         — {@code 'N'} = active; others = logically deleted</li>
 * </ul>
 *
 * <p>All column names match Java field names exactly — no {@code @ColumnName} needed.
 */
@Data
public class FinTdsType {

    /** Primary key — DB type: bigint. */
    private long tdstypecode;

    /** Displayed in the TDS Name search popup — e.g. {@code "TDS ON TECHNICAL SERVICE"}. */
    private String tdsname;

    /**
     * TDS category: {@code 'S'} = Services (section 194C/J/H/I etc.),
     * {@code 'G'} = Goods (section 194Q).
     * Carried in {@link TdsResolution} for use in downstream transactions.
     */
    private String tdstype;

    /** {@code 'N'} = active; query always filters {@code WHERE ext = 'N'}. */
    private String ext;
}
