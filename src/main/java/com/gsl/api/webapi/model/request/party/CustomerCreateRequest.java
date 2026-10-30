package com.gsl.api.webapi.model.request.party;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.model.subledgerothers.FinSlGlEntry;
import com.gsl.api.model.udf.UdfValueEntry;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/** Body of {@code POST Customer/Create}: the shared sub-ledger record plus the 6 keys only the customer screen sends (sales side). */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class CustomerCreateRequest extends SubLedgerCreateRequest {

    @JsonProperty("pricelistcode")
    private Long pricelistcode;

    @JsonProperty("saltermcode")
    private Long saltermcode;

    @JsonProperty("saltradegrpcode")
    private Long saltradegrpcode;

    @JsonProperty("salformcode")
    @Builder.Default
    private Long salformcode = 0L;

    @JsonProperty("poOrderLimit")
    @Builder.Default
    private String poOrderLimit = "";

    @JsonProperty("gstinTaxpayerType")
    @Builder.Default
    private String gstinTaxpayerType = "";
}
