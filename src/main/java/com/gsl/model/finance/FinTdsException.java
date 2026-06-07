package com.gsl.model.finance;

import lombok.Data;

/**
 * Maps to {@code fintds_exception} — Vendor TDS Nature assignment table.
 *
 * <p>Each row represents a TDS nature <em>tagged</em> to a vendor sub-ledger.
 * When a vendor has rows here, only those tagged natures are available in the
 * TDS Name popup for that vendor. When no rows exist, all active natures in
 * {@code fintdstype} are available.
 *
 * <h3>Table semantics</h3>
 * <ul>
 *   <li>Normal assignment: {@code slcode} + {@code tdstypecode} set;
 *       {@code certificateno}, {@code dtfr}, {@code dtto}, {@code rate} = NULL.</li>
 *   <li>Exception (override): same row, but {@code certificateno}/{@code rate}/{@code dtfr}/
 *       {@code dtto} are additionally populated when a custom rate or certificate applies.</li>
 * </ul>
 *
 * <p>Only the three columns relevant to TDS name resolution are mapped here.
 * Other columns ({@code certificateno}, {@code dtfr}, {@code dtto}, {@code rate},
 * {@code max_limit}, {@code admou_code}) are not needed for this purpose.
 */
@Data
public class FinTdsException {

    /** Primary key. */
    private long code;

    /** FK → {@code finsl.slcode} — identifies the vendor sub-ledger. */
    private long slcode;

    /** FK → {@code fintdstype.tdstypecode} — the tagged TDS nature. DB type: bigint. */
    private long tdstypecode;
}
