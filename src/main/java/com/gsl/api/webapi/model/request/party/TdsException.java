package com.gsl.api.webapi.model.request.party;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One TDS nature tagged to a party ({@code finTDSExceptions[]}). Plain natures carry empty strings for the optional fields; an
 * "exception" nature carries a certificate, validity dates, a rate and a limit (HARs: CreateVendorTDSApplicable*).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TdsException {
    @Builder.Default
    @JsonProperty("code")
    private long code = 0;
    @JsonProperty("tdsTypeCode")
    private long tdsTypeCode;
    @JsonProperty("tdsTypeName")
    private String tdsTypeName;
    @JsonProperty("admOUCode")
    private long admOuCode;
    @JsonProperty("admOUName")
    private String admOuName;
    @Builder.Default
    @JsonProperty("certificateno")
    private String certificateNo = "";
    @Builder.Default
    @JsonProperty("dtfr")
    private String dateFrom = "";
    @Builder.Default
    @JsonProperty("dtto")
    private String dateTo = "";
    /** {@code ""} for a plain nature, a number for an exception nature. */
    @Builder.Default
    @JsonProperty("rate")
    private Object rate = "";
    @Builder.Default
    @JsonProperty("maxlimit")
    private Object maxLimit = "";

    /** A plain nature: tagged with no certificate, validity, rate or limit. */
    public static TdsException plain(long tdsTypeCode, String tdsTypeName, long admOuCode, String admOuName) {
        return TdsException.builder().tdsTypeCode(tdsTypeCode).tdsTypeName(tdsTypeName).admOuCode(admOuCode).admOuName(admOuName).build();
    }
}
