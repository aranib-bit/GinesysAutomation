package com.gsl.api.model.arvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET Finance/api/docnumberschemes item shape — confirmed via HAR. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocNumberSchemeEntry {
    private long code;
    private String name;
    private String docSchemeNumber;
    private String manual;
}
