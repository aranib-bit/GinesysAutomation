package com.gsl.api.webapi.model.request.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Query string of the per-document approval endpoints ({@code ?entityCode=AP&refCode=641432}). */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalDocumentQuery {
    @JsonProperty("entityCode")
    private final String entityCode;
    @JsonProperty("refCode")
    private final String refCode;

    public static ApprovalDocumentQuery of(String entityCode, String refCode) {
        return ApprovalDocumentQuery.builder().entityCode(entityCode).refCode(refCode).build();
    }
}
