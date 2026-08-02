package com.gsl.api.model.udf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of a Save request's {@code udfValues[]} — shape confirmed via HAR:
 * {@code {"fieldName":"udfstring01","value":null,"datatype":"editablelist"}}.
 *
 * <p>{@code value}'s Java type must match {@code datatype} (String for
 * String/Memo/EditableList/FixedList, a real number for Numeric, an ISO date string for Date) —
 * Jackson would otherwise serialize a mismatched type in a way the server's strict model binder
 * (confirmed elsewhere in this flow to reject unrecognized shapes outright) is likely to refuse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UdfValueEntry {
    private String fieldName;
    private Object value;
    private String datatype;
}
