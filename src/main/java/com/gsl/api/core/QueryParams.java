package com.gsl.api.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/** Turns a query POJO into the parameter map RestAssured sends: names from {@code @JsonProperty}, null fields left out, lists repeated. */
public final class QueryParams {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QueryParams() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(Object query) {
        return new LinkedHashMap<>(MAPPER.convertValue(query, Map.class));
    }
}
