package com.gsl.api.webapi.model.request.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/** Query string of the approval endpoints that take one record id ({@code ?id=8}): a rule or a hierarchy. */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalIdQuery {
    @JsonProperty("id")
    private final Integer id;

    public static ApprovalIdQuery of(int id) {
        return ApprovalIdQuery.builder().id(id).build();
    }
}
