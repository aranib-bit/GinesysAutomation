package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** {@code GET /tdssection/tdsname}: the TDS natures (name, section, rate, agency) offered for a sub-ledger on an AP Voucher. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TdsSectionQuery extends LookupQuery {
    public static final List<String> QUERY_FIELDS = List.of("tdsName", "section", "tdsSourceName", "tdsSourceTypeDisplay");

    @Builder.Default
    @JsonProperty("query")
    private String query = "";
    @JsonProperty("pagingNotRequired")
    private boolean pagingNotRequired;
    @JsonProperty("slCode")
    private long slCode;
    @JsonProperty("srcCode")
    private int srcCode;
    @JsonProperty("voucherDate")
    private String voucherDate;
    @Builder.Default
    @JsonProperty("sort")
    private String sort = "tdsName ASC";
}
