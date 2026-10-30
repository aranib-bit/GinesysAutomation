package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** {@code GET /sites?target=referencesite}: the reference sites of a sub-ledger; the GL is only sent when known (AR omits it). */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReferenceSiteQuery extends LookupQuery {
    @Builder.Default
    @JsonProperty("target")
    private String target = "referencesite";
    @JsonProperty("slCode")
    private Long slCode;
    @JsonProperty("glCode")
    private Long glCode;
    @JsonProperty("connectedSiteOUCode")
    private Integer connectedSiteOuCode;
    @Builder.Default
    @JsonProperty("page")
    private int page = 1;
    @Builder.Default
    @JsonProperty("start")
    private int start = 0;
    @Builder.Default
    @JsonProperty("limit")
    private int limit = 500;

    public static ReferenceSiteQueryBuilder<?, ?> forModule(String module, int site, int ou, String workDate, String siteType, long slCode, long glCode) {
        return ReferenceSiteQuery.builder().module(module).connectedSite(site).connectedOuCode(ou).connectedSiteOuCode(ou)
                .connectedSiteType(siteType).workDate(workDate).slCode(slCode).glCode(glCode > 0 ? glCode : null)
                .queryFieldName(List.of("name", "siteType", "ouName"));
    }
}
