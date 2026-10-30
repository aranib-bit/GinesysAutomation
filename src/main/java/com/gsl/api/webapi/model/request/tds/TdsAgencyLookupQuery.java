package com.gsl.api.webapi.model.request.tds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.webapi.model.request.voucher.LookupQuery;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** The agency form's lookups ({@code SubLedgers} for its class, {@code Ledgers} for its ledger) under {@code module=tdsagency}. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TdsAgencyLookupQuery extends LookupQuery {
    @JsonProperty("connectedSiteOUCode")
    private Integer connectedSiteOuCode;
    @JsonProperty("page")
    private int page;
    @JsonProperty("start")
    private int start;
    @JsonProperty("limit")
    private int limit;
}
