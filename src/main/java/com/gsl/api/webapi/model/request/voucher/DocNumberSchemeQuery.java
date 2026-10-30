package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code GET /docnumberschemes}: the numbering schemes of a menu (the HAR sends {@code connectedSite}; an older capture used the lower-case spelling, the server accepts both). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocNumberSchemeQuery {
    @JsonProperty("connectedSite")
    private int connectedSite;
    @JsonProperty("menusysshortcode")
    private String menuShortCode;
    @Builder.Default
    @JsonProperty("pagingNotRequired")
    private Boolean pagingNotRequired = true;
    @JsonProperty("workDate")
    private String workDate;
    @JsonProperty("page")
    private Integer page;
    @JsonProperty("limit")
    private Integer limit;
}
