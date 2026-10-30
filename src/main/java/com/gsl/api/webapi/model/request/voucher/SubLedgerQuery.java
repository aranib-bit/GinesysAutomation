package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** {@code GET /subledgers}: every classification (all pages) or the picker's search (first page of a {@code searchText}). */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubLedgerQuery extends LookupQuery {
    @JsonProperty("connectedSiteOUCode")
    private Integer connectedSiteOuCode;
    @JsonProperty("pagingNotRequired")
    private Boolean pagingNotRequired;

    public static SubLedgerQueryBuilder<?, ?> forModule(String module, int site, long user, int ou, String workDate, String siteType) {
        return SubLedgerQuery.builder().module(module).connectedSite(site).connectedUserCode(user).connectedOuCode(ou).connectedSiteOuCode(ou)
                .connectedSiteType(siteType).workDate(workDate).searchText("").queryFieldName(List.of("slName", "className"));
    }
}
