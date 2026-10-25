package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Item of {@code GET Finance/api/sites?target=costcentertagging&glCode=…[&slCode=…]} — the sites selectable in a General Voucher
 * line's site-allocation popup, confirmed via HAR: {@code {"ext":null,"code":98,"name":"HAPPY SPROUTS-KP Road",
 * "siteType":"MS-OO-CM","shortName":"RVM2","ouCode":1,"isDefaultSite":null,"blockFin":"N"}}. Convert to a
 * {@link CostCenterAllocation} by adding the allocated amount.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CostCenterSiteLookupResult {
    private String ext;
    private long code;
    private String name;
    private String siteType;
    private String ouName;
    private String shortName;
    private String city;
    private Long ouCode;
    private Boolean isDefaultSite;
    /** {@code "Y"} when finance postings are blocked for the site. */
    private String blockFin;

    /** Builds the allocation entry for {@code amount} (the OU code falls back to {@code fallbackOuCode} when the lookup omits it). */
    public CostCenterAllocation toAllocation(double amount, long fallbackOuCode) {
        return CostCenterAllocation.builder()
                .code(code).name(name).siteType(siteType).shortName(shortName)
                .amount(amount).ouCode(ouCode != null ? ouCode : fallbackOuCode)
                .build();
    }
}
