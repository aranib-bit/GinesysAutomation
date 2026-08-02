package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A {@code { code: N }} style lookup reference — the shape k6's payloads use for nested
 * references such as {@code invgrp}, {@code invcat1}, {@code invhsnsacmain}, {@code admsite}, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LookupValue {
    private Integer code;
    private String name;

    public static LookupValue ofCode(int code) {
        return LookupValue.builder().code(code).build();
    }
}
