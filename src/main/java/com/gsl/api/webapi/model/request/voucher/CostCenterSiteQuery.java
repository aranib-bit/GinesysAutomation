package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** {@code GET /sites?target=costcentertagging}: the sites selectable for a line's cost-centre allocation (one page is added by the pager). */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CostCenterSiteQuery extends LookupQuery {
    @JsonProperty("connectedSiteOUCode")
    private Integer connectedSiteOuCode;
    @Builder.Default
    @JsonProperty("target")
    private String target = "costcentertagging";
    @JsonProperty("glCode")
    private Long glCode;
    @JsonProperty("slCode")
    private Long slCode;
    /** The UI sends this one lower-case in addition to {@code connectedOUCode}. */
    @JsonProperty("connectedoucode")
    private Integer connectedOuCodeLower;
    @Builder.Default
    @JsonProperty("query")
    private String query = "";
    @Builder.Default
    @JsonProperty("sort")
    private String sort = "name ASC";
    @Builder.Default
    @JsonProperty("pagingNotRequired")
    private Boolean pagingNotRequired = false;

    public static CostCenterSiteQueryBuilder<?, ?> forModule(String module, int site, long user, int ou, String workDate, String siteType, long glCode, Long slCode) {
        return CostCenterSiteQuery.builder().module(module).connectedSite(site).connectedUserCode(user).connectedOuCode(ou).connectedSiteOuCode(ou)
                .connectedOuCodeLower(ou).connectedSiteType(siteType).workDate(workDate).searchText("").glCode(glCode).slCode(slCode)
                .queryFieldName(List.of("name", "shortName", "siteType", "city"));
    }
}
