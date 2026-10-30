package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code GET DocumentAdjustments/{ap|ar}/reference?target=referencesiteforfifo}: the pending amount per reference site of a sub-ledger. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceSitePendingQuery {
    @Builder.Default
    @JsonProperty("searchText")
    private String searchText = "";
    @Builder.Default
    @JsonProperty("target")
    private String target = "referencesiteforfifo";
    @JsonProperty("module")
    private String module;
    @JsonProperty("ouCode")
    private int ouCode;
    @JsonProperty("slCode")
    private long slCode;
    @JsonProperty("glCode")
    private long glCode;
    @JsonProperty("connectedOUCode")
    private int connectedOuCode;
    @JsonProperty("date")
    private String date;
    @Builder.Default
    @JsonProperty("limit")
    private int limit = 50;
    @JsonProperty("workDate")
    private String workDate;
    @Builder.Default
    @JsonProperty("page")
    private int page = 1;
    @Builder.Default
    @JsonProperty("start")
    private int start = 0;
}
