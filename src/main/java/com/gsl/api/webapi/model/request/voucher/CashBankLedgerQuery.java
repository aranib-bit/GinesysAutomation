package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** {@code GET /ledgers/cashbankledgers}: the header Cash/Bank ledgers. The HAR sends no {@code connectedSiteOUCode} here. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CashBankLedgerQuery extends LookupQuery {
    @Builder.Default
    @JsonProperty("query")
    private String query = "";
    @Builder.Default
    @JsonProperty("sort")
    private String sort = "glName ASC";
    @Builder.Default
    @JsonProperty("pagingNotRequired")
    private Boolean pagingNotRequired = false;

    public static CashBankLedgerQueryBuilder<?, ?> forModule(String module, int site, long user, int ou, String workDate, String siteType) {
        return CashBankLedgerQuery.builder().module(module).connectedSite(site).connectedUserCode(user).connectedOuCode(ou)
                .connectedSiteType(siteType).workDate(workDate).searchText("").queryFieldName(List.of("glName", "abbreviation"));
    }
}
