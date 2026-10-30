package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** The parameters every Finance lookup of a voucher module sends (HARs: APVoucherFlow, ARVoucherFlow); each endpoint adds its own. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class LookupQuery {
    @JsonProperty("module")
    private String module;
    @JsonProperty("connectedSite")
    private Integer connectedSite;
    @JsonProperty("connectedUserCode")
    private Long connectedUserCode;
    @JsonProperty("connectedOUCode")
    private Integer connectedOuCode;
    @JsonProperty("connectedSiteType")
    private String connectedSiteType;
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("searchText")
    private String searchText;
    @JsonProperty("queryFieldName")
    private List<String> queryFieldName;
}
