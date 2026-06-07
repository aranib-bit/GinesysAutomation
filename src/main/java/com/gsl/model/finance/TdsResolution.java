package com.gsl.model.finance;

import lombok.Value;

/**
 * Immutable result of TDS name resolution for a vendor SL.
 *
 * <p>Produced by {@link com.gsl.database.query.TdsNameQuery#resolve} and stored in
 * {@link com.gsl.utils.dataprovider.fixture.LiveDbRefs#getSlToTdsResolution()},
 * keyed by {@code "slName|slId"}.
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code tdsName} is <b>never null</b> — {@code TdsNameQuery} throws
 *       {@link IllegalStateException} if resolution fails rather than returning null.</li>
 *   <li>{@code tdsType} is {@code 'S'} (Services) or {@code 'G'} (Goods) — sourced from
 *       {@code fintdstype.tdstype}; required by downstream transactions.</li>
 * </ul>
 */
@Value
public class TdsResolution {

    /**
     * TDS name as displayed in the UI TDS Name popup.
     * Populated into {@link com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.TdsAndNetPayable#getTdsName()}.
     * Never null.
     */
    String tdsName;

    /**
     * TDS category sourced from {@code fintdstype.tdstype}:
     * {@code 'S'} = Services, {@code 'G'} = Goods.
     * Carried for use in other transactions.
     */
    String tdsType;
}
