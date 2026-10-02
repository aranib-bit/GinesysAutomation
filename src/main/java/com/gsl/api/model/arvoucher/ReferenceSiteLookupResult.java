package com.gsl.api.model.arvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET Finance/api/sites?target=referencesite item shape — confirmed via HAR. Used by both
 * {@code ApVoucherApiClient} and {@code ArVoucherApiClient} to resolve a create request's
 * {@code referenceSiteCode}, preferring the owner site's own entry when present.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceSiteLookupResult {
    /** TODO-confirm: not consumed anywhere else in this framework; possibly an extinct/active
     *  flag mirroring the DB's own {@code ext} column convention (e.g. {@code FinTdsTypeDao}'s
     *  {@code ext = 'N'} filter) but that has not been verified against this endpoint. */
    private String ext;
    private long code;
    private String name;
    private String siteType;
    private String ouName;
}
