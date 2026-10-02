package com.gsl.api.model.item;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of {@code ItemApiValidator.validate()} — pass/fail plus the reason, for report logging.
 * Use {@link #pass()}/{@link #fail(String)} rather than the Lombok builder directly, so a passing
 * result can't accidentally carry a stray {@code reason}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemValidationResult {
    private boolean valid;
    private String reason;

    /** A passing validation result — {@code reason} left null. */
    public static ItemValidationResult pass() {
        return ItemValidationResult.builder().valid(true).build();
    }

    /** A failing validation result carrying {@code reason} for report logging. */
    public static ItemValidationResult fail(String reason) {
        return ItemValidationResult.builder().valid(false).reason(reason).build();
    }
}
