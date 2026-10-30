package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET Finance/api/docnumberschemes item shape — confirmed via HAR. Used by both
 * {@code ApVoucherApiClient} and {@code ArVoucherApiClient} to resolve the doc scheme
 * (by {@code menusysshortcode}/{@code name}) before building a create request's
 * {@code docSchemeCode}/{@code docNumber} pair.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocNumberSchemeEntry {
    private long code;
    private String name;
    private String docSchemeNumber;
    /** Coded manual-numbering flag for this scheme — see {@code ApVoucherCreateRequest#docNumber}/
     *  {@code ArVoucherCreateRequest#docNumber}, which are only populated when this is set. */
    private String manual;
}
