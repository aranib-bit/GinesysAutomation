package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gsl.api.model.common.LookupValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;

/**
 * POST GRC/Save request body — the WebAPI wire shape {@code GrcApiClient} builds and posts to
 * create a Goods Receipt Challan. A reduced field set relative to flows/createGrc.js's full save
 * body (that k6 flow resolves charges/margin-validation/tax-region context via separate lookup
 * calls before Save); this DTO carries only what's needed for a minimal single-vendor,
 * single-line GRC. {@code admsite}/{@code admouCode} are the receiving site/OU
 * {@link LookupValue}s, {@code grcdt} is an ISO date-time string, and {@code rcqty}/
 * {@code grsamt} are header-level totals mirroring the sum of {@code items}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrcCreateRequest {
    @Builder.Default
    private int code = 0;
    private int vendorCode;
    private LookupValue admsite;
    private LookupValue admouCode;
    private String grcdt; // ISO date-time, e.g. "2026-07-27T00:00:00"

    @Singular
    private List<GrcLineItem> items;
    @Singular("charge")
    private List<GrcCharges> grcCharges;

    private Integer rcqty;
    private Integer grsamt;
}
