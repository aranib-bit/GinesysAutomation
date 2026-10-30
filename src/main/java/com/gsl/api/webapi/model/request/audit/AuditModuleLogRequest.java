package com.gsl.api.webapi.model.request.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code POST /AuditLog/GetAuditModuleLog} body (the UI capitalises these keys). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AuditModuleLogRequest {
    @JsonProperty("ModuleShortCode")
    private String moduleShortCode;
    @JsonProperty("ModuleShortName")
    private String moduleShortName;
    @JsonProperty("FromDate")
    private String fromDate;
    @JsonProperty("ToDate")
    private String toDate;
    @JsonProperty("User")
    private String user;
}
