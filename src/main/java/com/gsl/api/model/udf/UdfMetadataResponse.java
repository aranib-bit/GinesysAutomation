package com.gsl.api.model.udf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * {@code GET /udf/GetUDFMetaData}'s {@code {result:{metaData:[...]}, success, error}} envelope —
 * deserialized directly in {@code UdfValueBuilder.fetchUdfMetadata(ApiClient, String)} via
 * {@code response.as(UdfMetadataResponse.class)}. {@link #getMetaData()} flattens the nested
 * {@link Result#metaData} onto this class so callers never need to reach into {@link #result}
 * directly (mirrors {@code TransferOutCreateResponse}'s delegating-getter pattern for the same
 * kind of nested envelope).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UdfMetadataResponse {

    private boolean success;
    private String error;
    private Result result;

    /** Delegates to {@link Result#metaData}, or an empty list if {@link #result} is absent. */
    public List<UdfFieldMetadata> getMetaData() {
        return result != null ? result.metaData : List.of();
    }

    /** The field-metadata list — {@code result} in the raw JSON. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @Builder.Default
        private List<UdfFieldMetadata> metaData = List.of();
    }
}
