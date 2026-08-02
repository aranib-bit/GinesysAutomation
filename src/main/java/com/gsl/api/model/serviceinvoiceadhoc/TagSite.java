package com.gsl.api.model.serviceinvoiceadhoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One entry of a service line's {@code tagSites} — the owner/reference site pairing for a charge. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagSite {
    private String ownerSiteName;
    private int ownerSiteCode;
    private String refSiteName;
    private int refSiteCode;
    private String ownerSiteType;
    private String refSiteType;
    private int amount;
}
