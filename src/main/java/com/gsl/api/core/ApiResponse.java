package com.gsl.api.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic envelope wrapper matching the {@code {result, success, error}} shape every
 * WebAPI/PublicAPI endpoint returns (verified via captured HAR — e.g. Session/Logout's
 * {@code {"result":null,"success":true,"error":null}}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private T result;
    private boolean success;
    private String error;
}
