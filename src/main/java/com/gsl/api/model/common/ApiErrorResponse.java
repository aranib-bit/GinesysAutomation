package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard error envelope shape returned by WebAPI/PUBLICAPI on failure: {result, success, error}.
 * A shared, generic shape under {@code api.model.common} — not tied to any single endpoint or
 * client in this codebase; a client deserializes into it wherever it needs to inspect a failure
 * envelope shaped like this one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private Object result;
    private boolean success;
    private String error;
}
