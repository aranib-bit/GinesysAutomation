package com.gsl.api.client.udf;

import com.gsl.api.core.ApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.udf.UdfFieldMetadata;
import com.gsl.api.model.udf.UdfLookupValue;
import com.gsl.api.model.udf.UdfMetadataResponse;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.database.DatabaseManager;
import com.gsl.database.query.UdfValueQuery;
import com.gsl.reports.ReportLogger;
import io.restassured.response.Response;
import org.jdbi.v3.core.Jdbi;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Table-parameterized UDF metadata/value-building logic — extracted out of
 * {@code ArVoucherApiClient} (originally hardcoded to {@code FINVCHMAIN}) so it can be reused
 * as-is by {@code SubLedgerOthersApiClient} for {@code FINSL}. Behavior is unchanged from the
 * original AR Voucher implementation, just relocated and parameterized by {@code tableName}.
 *
 * <p>Endpoints live under the plain {@code WebAPI} base (relative paths, no
 * {@code financeApiBaseUri} prefix) — confirmed via HAR path {@code /WebAPI/api/udf/GetUDFMetaData}
 * for both {@code FINVCHMAIN} (AR Voucher) and {@code FINSL} (Sub Ledger Others).
 */
public final class UdfValueBuilder {

    private UdfValueBuilder() {}

    private static final int UNIQUE_UDF_VALUE_MAX_ATTEMPTS = 5;

    /** GET /udf/GetUDFMetaData — see class Javadoc for the endpoint's base-path note. */
    public static List<UdfFieldMetadata> fetchUdfMetadata(ApiClient client, String tableName) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("_dc", System.currentTimeMillis());
        params.put("tableName", tableName);
        params.put("lookupData", true);
        Response response = client.getRaw("/udf/GetUDFMetaData", params);
        return response.as(UdfMetadataResponse.class).getMetaData();
    }

    /** Fetches metadata then builds values in one call — the common case for every caller. */
    public static List<UdfValueEntry> fetchAndBuild(ApiClient client, String tableName) {
        List<UdfFieldMetadata> metadata = fetchUdfMetadata(client, tableName);
        return buildUdfValues(metadata, tableName);
    }

    /**
     * Builds {@code udfValues} from live metadata — one {@link UdfValueEntry} per field, matching
     * the exact shape confirmed via HAR: {@code ""} for String/Memo, {@code null} otherwise, UNLESS
     * a field is mandatory, in which case a real, datatype-correct value is required instead (and
     * DB-validated against {@code tableName} first when the field is also {@code isUnique}).
     */
    public static List<UdfValueEntry> buildUdfValues(List<UdfFieldMetadata> metadata, String tableName) {
        List<UdfValueEntry> entries = new ArrayList<>();
        for (UdfFieldMetadata field : metadata) {
            String fieldName = field.getColumnName().toLowerCase();
            String datatype = field.getDatatype().toLowerCase();
            Object value = field.isMandatory()
                    ? resolveMandatoryUdfValue(field, datatype, tableName)
                    : defaultUdfValue(datatype);
            entries.add(UdfValueEntry.builder().fieldName(fieldName).value(value).datatype(datatype).build());
            ReportLogger.dataBackfill(
                    "[UdfValueBuilder] UDF field '{}' (table={}, datatype={}, mandatory={}, unique={}) -> value={}",
                    fieldName, tableName, datatype, field.isMandatory(), field.isUnique(), value);
        }
        return entries;
    }

    /** Non-mandatory default — matches the real captured request's own pattern exactly. */
    private static Object defaultUdfValue(String datatypeLower) {
        return ("string".equals(datatypeLower) || "memo".equals(datatypeLower)) ? "" : null;
    }

    /**
     * Generates a real value for a mandatory field, re-generating up to
     * {@value #UNIQUE_UDF_VALUE_MAX_ATTEMPTS} times if the field is also {@code isUnique} and a
     * candidate collides with an existing row in {@code tableName} — never sends a known-duplicate
     * value and lets the server reject it opaquely.
     */
    private static Object resolveMandatoryUdfValue(UdfFieldMetadata field, String datatypeLower, String tableName) {
        Jdbi jdbi = field.isUnique() ? DatabaseManager.getJdbi() : null;

        for (int attempt = 0; attempt < UNIQUE_UDF_VALUE_MAX_ATTEMPTS; attempt++) {
            Object candidate = generateUdfCandidate(field, datatypeLower, attempt);
            if (jdbi == null || !UdfValueQuery.existsInTable(jdbi, tableName, field.getColumnName(), candidate)) {
                return candidate;
            }
            ReportLogger.dataBackfillWarn(
                    "[UdfValueBuilder] UDF value collision for '{}' (table={}) — candidate {} already exists, retrying ({}/{})",
                    field.getColumnName(), tableName, candidate, attempt + 1, UNIQUE_UDF_VALUE_MAX_ATTEMPTS);
        }
        throw new ApiException("Could not generate a unique value for mandatory UDF field '"
                + field.getColumnName() + "' (table=" + tableName + ") after " + UNIQUE_UDF_VALUE_MAX_ATTEMPTS + " attempts.", 0);
    }

    /** Datatype-correct Java-typed candidate. */
    private static Object generateUdfCandidate(UdfFieldMetadata field, String datatypeLower, int attempt) {
        return switch (datatypeLower) {
            case "fixedlist" -> pickFixedListCandidate(field, attempt);
            case "editablelist", "string", "memo" -> "AUTO_" + System.nanoTime() + (attempt > 0 ? "_" + attempt : "");
            case "numeric" -> (System.currentTimeMillis() % 1_000_000) + attempt;
            case "date" -> LocalDate.now().plusDays(attempt) + "T00:00:00";
            default -> throw new ApiException("Unrecognized UDF datatype '" + field.getDatatype()
                    + "' for mandatory field '" + field.getColumnName() + "'.", 0);
        };
    }

    /**
     * Picks a valid FixedList choice — prefers {@code lookupData.data} (populated for the
     * "Comma Separated" sub-mode, and would cover "Select Query" too if it ever comes back
     * populated), falling back to {@code listValues.split(",")}. A mandatory, Select-Query-sourced
     * field with no pre-resolved candidates has no verified, safe way to derive a value — throws
     * rather than attempting untested {@code lookupQuery} placeholder substitution.
     */
    private static Object pickFixedListCandidate(UdfFieldMetadata field, int attempt) {
        List<String> candidates = new ArrayList<>();
        if (field.getLookupData() != null && field.getLookupData().getData() != null) {
            for (UdfLookupValue v : field.getLookupData().getData()) {
                if (v.getValue() != null) candidates.add(v.getValue());
            }
        }
        if (candidates.isEmpty() && field.getListValues() != null && !field.getListValues().isBlank()) {
            candidates.addAll(Arrays.asList(field.getListValues().split(",")));
        }
        if (candidates.isEmpty()) {
            if (field.isSqlData() || "S".equalsIgnoreCase(field.getQueryType())) {
                throw new ApiException("Mandatory Select-Query FixedList field '" + field.getColumnName()
                        + "' has no pre-resolved lookup data — lookupQuery execution with "
                        + "<Connected Site>/<WorkDate> substitution isn't implemented; needs a live captured example first.", 0);
            }
            throw new ApiException("Mandatory FixedList field '" + field.getColumnName()
                    + "' has no listValues/lookupData to choose from.", 0);
        }
        if (attempt >= candidates.size()) {
            throw new ApiException("Exhausted all " + candidates.size() + " candidate value(s) for mandatory+unique "
                    + "FixedList field '" + field.getColumnName() + "' — every value already in use.", 0);
        }
        return candidates.get(attempt).trim();
    }
}
