package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One GRC-level charge line (freight, handling, etc.) — grcCharges[] in flows/createGrc.js. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrcCharges {
    private String chargeCode;
    private Integer amount;
}
