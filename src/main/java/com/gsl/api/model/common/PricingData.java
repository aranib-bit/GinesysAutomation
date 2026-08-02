package com.gsl.api.model.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cost/WSP/MRP/listed-MRP quadruple used across Item/GRC/DC line pricing.
 * {@link #jittered} applies the same jitter formula k6 uses, preserving ascending order
 * (costrate &lt; wsp &lt; mrp &lt; listedMrp).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingData {
    private int costrate;
    private int wsp;
    private int mrp;
    private int listedMrp;

    /** jitter = 1 + ((threadId*7 + iterId*3) % 50) / 100 — matches k6's per-VU/iteration pricing jitter. */
    public static PricingData jittered(int baseCostrate, int baseWsp, int baseMrp, int baseListedMrp,
                                        long threadId, int iterId) {
        double jitter = 1 + ((threadId * 7 + iterId * 3) % 50) / 100.0;
        return PricingData.builder()
                .costrate((int) Math.round(baseCostrate * jitter))
                .wsp((int) Math.round(baseWsp * jitter))
                .mrp((int) Math.round(baseMrp * jitter))
                .listedMrp((int) Math.round(baseListedMrp * jitter))
                .build();
    }
}
