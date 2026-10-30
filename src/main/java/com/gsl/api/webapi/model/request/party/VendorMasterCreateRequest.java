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

/**
 * Body of {@code POST VendorMaster/Create}. One class serves every Vendor Master class type (S, J, A, T, E, H): only {@code clscode}
 * differs (Jobber HAR: identical 134 keys, Supplier 2 vs Jobber 4). Adds the 7 keys the customer body does not have.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class VendorMasterCreateRequest extends SubLedgerCreateRequest {

    @JsonProperty("cmpcode")
    private Long cmpcode;

    @JsonProperty("allowjobberstockpoint")
    @Builder.Default
    private String allowjobberstockpoint = "N";

    @JsonProperty("prdmaxdays")
    @Builder.Default
    private Long prdmaxdays = 0L;

    @JsonProperty("prdoverdueaction")
    @Builder.Default
    private String prdoverdueaction = "N";

    @JsonProperty("saltremail")
    @Builder.Default
    private String saltremail = "";

    @JsonProperty("transporterid")
    private String transporterid;

    @JsonProperty("gstinTaxPayerType")
    @Builder.Default
    private String gstinTaxPayerType = "";
}
