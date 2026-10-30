package com.gsl.api.webapi.model.request.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code POST /AuditLog/GetApplicableAuditModules} body. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApplicableAuditModulesRequest {
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("page")
    private int page;
    @JsonProperty("start")
    private int start;
    @JsonProperty("limit")
    private int limit;
}
