package com.gsl.api.model.udf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of {@code GET /udf/GetUDFMetaData}'s {@code result.metaData} — shape confirmed via
 * HAR ({@code CreateARVoucherstage-ideal.ginesys.cloud4.har}, tableName=FINVCHMAIN).
 *
 * <p>{@code datatype} is one of {@code EditableList}, {@code FixedList}, {@code String},
 * {@code Memo}, {@code Numeric}, {@code Date} (confirmed live for all six).
 *
 * <p>{@code FixedList} has two sourcing sub-modes, both distinguishable from this metadata alone
 * (confirmed against the app's own "User Defined Fields" admin screen):
 * <ul>
 *   <li>Comma Separated — {@code queryType="C"}, {@code isSqlData=false}, {@code listValues} set,
 *       and {@code lookupData.data} pre-populated by the server with those same values.</li>
 *   <li>Select Query — {@code queryType="S"}, {@code isSqlData=true}, a real {@code lookupQuery}
 *       (with substitutable placeholders like {@code <Connected Site>}/{@code <WorkDate>} per the
 *       admin screen), and {@code lookupData.data} comes back EMPTY — the server does not
 *       pre-resolve query-sourced candidates.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UdfFieldMetadata {
    private long code;
    private String tableName;
    private String columnName;
    private String displayName;
    private boolean isUnique;
    private boolean isMandatory;
    private String datatype;
    private boolean enabled;
    private Integer maxLength;
    private String listValues;
    private boolean isLookup;
    private String lookupQuery;
    private boolean isSqlData;
    /** "C" = Comma Separated, "S" = Select Query (see class Javadoc). */
    private String queryType;
    private UdfLookupData lookupData;
}
