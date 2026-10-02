package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One field-level validation error, as returned by endpoints like ValidateItemsForPriceChart /
 * ValidateMarginRule. A shared, generic shape under {@code api.model.common} — not currently
 * deserialized by any client in this codebase; kept here for whichever client is built against
 * one of those endpoints next.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationError {
    private String field;
    private String message;
    private boolean hasError;
}
