package com.gsl.api.model.udf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GetUDFMetaData's per-field {@code lookupData} — pre-resolved candidate values. Confirmed via
 * HAR: populated for "Comma Separated" FixedList fields (same values as {@code listValues}), but
 * empty ({@code data:[]}) for "Select Query"-sourced fields (queryType 'S') — the server doesn't
 * pre-resolve those, they'd need live {@code lookupQuery} execution with placeholder substitution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UdfLookupData {
    @Builder.Default
    private List<UdfLookupValue> data = List.of();
    private List<String> columnList;
}
