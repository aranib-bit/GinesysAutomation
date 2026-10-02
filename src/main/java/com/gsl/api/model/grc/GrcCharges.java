package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One GRC-level charge line (freight, handling, etc.) inside
 * {@link GrcCreateRequest#getGrcCharges()} — mirrors flows/createGrc.js's {@code grcCharges[]},
 * which that k6 flow computes server-side via {@code Utility/CalculateItemCharges} rather than
 * inventing client-side. Not currently populated by {@code GrcApiClient.createGrc(...)}'s
 * single-line builder call (its {@code grcCharges} list stays empty); this DTO is the shape to
 * submit charges in once that lookup is wired into the Java client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrcCharges {
    private String chargeCode;
    private Integer amount;
}
