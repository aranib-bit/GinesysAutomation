package com.gsl.api.webapi.model.request.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Query string of {@code GetTabWiseAuditTransactonDetails}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditHistoryQuery {
    @JsonProperty("code")
    private String code;
    @JsonProperty("tableName")
    private String tableName;
    @JsonProperty("moduleShortCode")
    private String moduleShortCode;
    @JsonProperty("fromDate")
    private String fromDate;
    @JsonProperty("toDate")
    private String toDate;
    @JsonProperty("revisionFilter")
    private boolean revisionFilter;
}
