package com.gsl.api.webapi.model.request.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Query string of the approval endpoints that take one entity ({@code ?entityCode=AP}). */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalEntityQuery {
    @JsonProperty("entityCode")
    private final String entityCode;

    public static ApprovalEntityQuery of(String entityCode) {
        return ApprovalEntityQuery.builder().entityCode(entityCode).build();
    }
}
