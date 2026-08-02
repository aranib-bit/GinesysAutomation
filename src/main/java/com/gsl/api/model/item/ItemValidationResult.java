package com.gsl.api.model.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Outcome of ItemApiValidator.validate() — pass/fail plus the reason, for report logging. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemValidationResult {
    private boolean valid;
    private String reason;

    public static ItemValidationResult pass() {
        return ItemValidationResult.builder().valid(true).build();
    }

    public static ItemValidationResult fail(String reason) {
        return ItemValidationResult.builder().valid(false).reason(reason).build();
    }
}
