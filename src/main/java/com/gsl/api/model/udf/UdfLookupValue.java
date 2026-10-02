package com.gsl.api.model.udf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of {@code GET /udf/GetUDFMetaData}'s {@code lookupData.data} — shape confirmed via
 * HAR: {@code {"value": "..."}}. Wraps {@link UdfFieldMetadata#getLookupData()}'s candidate
 * values; {@code UdfValueBuilder.pickFixedListCandidate} reads {@link #value} off these when
 * choosing a value for a mandatory {@code FixedList} UDF field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UdfLookupValue {
    private String value;
}
