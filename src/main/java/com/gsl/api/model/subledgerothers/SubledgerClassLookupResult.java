package com.gsl.api.model.subledgerothers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One item of {@code POST /Shared/GetSubledgerClassLookup}'s {@code result.items} — shape
 * confirmed via HAR ({@code CreateSubLedgerOthers_stage-ideal...har}, {@code classType=O}):
 * {@code {"name":"Others","type":"O","typeDisplay":"Others","ext":"N","code":6}}. {@code code} is
 * the {@code admcls.clscode} to send as {@code SubLedgerOthersCreateRequest.clscode} — resolved
 * live here rather than hardcoding {@code 6}, same principle as
 * {@code ArVoucherApiClient.resolveSubLedger} never hardcoding a classification code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubledgerClassLookupResult {
    private long code;
    private String name;
    private String type;
    private String typeDisplay;
    private String ext;
}
