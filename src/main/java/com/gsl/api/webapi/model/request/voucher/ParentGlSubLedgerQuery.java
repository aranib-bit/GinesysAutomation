package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** {@code GET /subledgers?parentGLCode=}: the sub-ledgers a sub-ledger-applicable GL permits (General Voucher's grid). */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParentGlSubLedgerQuery extends LookupQuery {
    @JsonProperty("connectedSiteOUCode")
    private Integer connectedSiteOuCode;
    @JsonProperty("parentGLCode")
    private Long parentGlCode;
    @Builder.Default
    @JsonProperty("query")
    private String query = "";

    public static ParentGlSubLedgerQueryBuilder<?, ?> forModule(String module, int site, long user, int ou, String workDate, String siteType, long parentGl) {
        return ParentGlSubLedgerQuery.builder().module(module).connectedSite(site).connectedUserCode(user).connectedOuCode(ou).connectedSiteOuCode(ou)
                .connectedSiteType(siteType).workDate(workDate).searchText("").parentGlCode(parentGl)
                .queryFieldName(List.of("slName", "className"));
    }
}
