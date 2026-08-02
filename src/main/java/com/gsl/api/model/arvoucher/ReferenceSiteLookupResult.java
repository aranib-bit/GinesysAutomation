package com.gsl.api.model.arvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET Finance/api/sites?target=referencesite item shape — confirmed via HAR. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReferenceSiteLookupResult {
    private String ext;
    private long code;
    private String name;
    private String siteType;
    private String ouName;
}
