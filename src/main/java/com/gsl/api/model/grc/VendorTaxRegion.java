package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of GET /GRC/GetGRCVendorTaxRegion — the vendor's tax region, needed to compute correct
 * TDS/GST on the GRC. Fetched by {@code GrcApiClient.fetchVendorTaxRegion(int)} for
 * logging/correctness parity with the k6 flow; the resolved value isn't yet threaded into
 * {@link GrcCreateRequest}'s payload (see that client method's own comment).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VendorTaxRegion {
    private String taxRegion;
    private boolean interState;
}
