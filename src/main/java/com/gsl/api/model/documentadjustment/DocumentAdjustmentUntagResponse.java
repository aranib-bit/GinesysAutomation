package com.gsl.api.model.documentadjustment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code Finance/api/documentAdjustments}'s {status, timestamp, message, result, dataVersion,
 * error, success} envelope — confirmed via HAR: a successful untag returns
 * {@code {"status":0,"message":"Document adjustment adjusted successfully.","result":"<scheme_docno>",
 * "success":true}}. Mirrors {@code ApVoucherCreateResponse}'s envelope shape, except {@code result}
 * here is a plain {@code String} (the document's own {@code scheme_docno}), not a nested object —
 * kept literal to the confirmed capture rather than assuming a richer shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentAdjustmentUntagResponse {

    private int status;
    private String timestamp;
    private String message;

    /** The untagged document's own {@code scheme_docno} — confirmed via HAR (e.g. {@code "VP/02484/26-27"}). */
    private String result;

    private String dataVersion;
    private String error;
    private boolean success;
}
