package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The {@code ownerSite} object of a General Voucher create/update body — confirmed via HAR. Unlike AR/AP (which send
 * {@code ownerSiteCode}), GV sends a PascalCase object: {@code {"code":1,"Name":"Hena Embroidery","SiteType":"OS-OO-CM",
 * "ShortName":"HEMB","OUName":"Hena Embroidery","OUCode":1}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralVoucherOwnerSite {
    private long code;
    @JsonProperty("Name")
    private String name;
    @JsonProperty("SiteType")
    private String siteType;
    @JsonProperty("ShortName")
    private String shortName;
    @JsonProperty("OUName")
    private String ouName;
    @JsonProperty("OUCode")
    private long ouCode;
}
