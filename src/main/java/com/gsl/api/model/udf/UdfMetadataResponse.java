package com.gsl.api.model.udf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** GET /udf/GetUDFMetaData's {result:{metaData:[...]}, success, error} envelope. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UdfMetadataResponse {

    private boolean success;
    private String error;
    private Result result;

    public List<UdfFieldMetadata> getMetaData() {
        return result != null ? result.metaData : List.of();
    }

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
