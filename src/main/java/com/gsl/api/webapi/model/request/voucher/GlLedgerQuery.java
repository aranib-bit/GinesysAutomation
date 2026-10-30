package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** {@code GET /ledgers?module=generalvoucher}: the GL lookup of the Expense/Income grid (all pages; {@code pagingNotRequired} asks for everything). */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlLedgerQuery extends LookupQuery {
    @JsonProperty("connectedSiteOUCode")
    private Integer connectedSiteOuCode;
    @Builder.Default
    @JsonProperty("pagingNotRequired")
    private Boolean pagingNotRequired = true;

    public static GlLedgerQueryBuilder<?, ?> forModule(String module, int site, long user, int ou, String workDate, String siteType) {
        return GlLedgerQuery.builder().module(module).connectedSite(site).connectedUserCode(user).connectedOuCode(ou).connectedSiteOuCode(ou)
                .connectedSiteType(siteType).workDate(workDate).searchText("")
                .queryFieldName(List.of("glName", "abbreviation", "groupName", "siteApplicable", "subLedgerApplicable"));
    }
}
