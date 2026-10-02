package com.gsl.api.model.documentadjustment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code POST Finance/api/documentAdjustments} request body — shape confirmed via a live HAR
 * capture (per the user's supplied capture, a multi-invoice-tagged document untagged in a single
 * call): removes <b>every</b> {@code fintag} row on one document in one shot by sending an empty
 * {@link #adjustedDocuments} list — the live app's own "clear all adjustments" behavior, not
 * something this framework infers/simulates.
 *
 * <p>{@code baseDocumentCode}/{@code baseDocumentNumber}/{@code baseDocumentActualAmount}/
 * {@code baseDocumentSlCode} always describe the document <b>under test</b> (the one being
 * reverted/deleted/edited) — never the counter-document it was tagged against — regardless of
 * whether it appears as {@code fintag.entcode1} or {@code entcode2} in the DB (confirmed via the
 * HAR: the same request shape untags a document that was itself the "invoice side" of the tag).
 *
 * <p>{@link #baseDocumentType} is a confirmed literal {@code "VDP"} for both AR Voucher and AP
 * Voucher documents — see {@code DocumentAdjustmentFlow}'s Javadoc for why this is kept as a
 * plain string constant rather than folded into an enum: the codebase's one existing adjustment-
 * related enum ({@code com.gsl.enums.AdjustmentType}) is a different, unrelated concept (an AR
 * sub-ledger reconciliation-strategy enum — FIFO/manual set-off), not a document-type discriminator,
 * and reusing/extending it here would conflate two unrelated ideas.
 *
 * <p>Mirrors {@code ApVoucherCreateRequest}'s Lombok shape ({@code @Data}/{@code @Builder}/
 * {@code @NoArgsConstructor}/{@code @AllArgsConstructor}) — the same convention every other
 * request DTO in this codebase's {@code api.model.*} packages already follows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAdjustmentUntagRequest {

    // Explicit @JsonProperty on every field — confirmed via HAR that this endpoint's request body
    // uses PascalCase keys ("ConnectedUserCode", "BaseDocumentType", ...), unlike every other
    // request DTO in this codebase's api.model.* packages (e.g. ApVoucherCreateRequest), which are
    // plain camelCase with no naming override. Kept literal to match the confirmed-working
    // capture rather than assuming a project-wide Jackson naming strategy that doesn't exist.

    @JsonProperty("ConnectedUserCode")
    private long connectedUserCode;

    @JsonProperty("ConnectedOUCode")
    private long connectedOUCode;

    /** Confirmed literal {@code "VDP"} for AR/AP Voucher documents — see class Javadoc. */
    @JsonProperty("BaseDocumentType")
    private String baseDocumentType;

    /** The document under test's own {@code finvchmain.vchcode} — never the counter-document's. */
    @JsonProperty("BaseDocumentCode")
    private long baseDocumentCode;

    /** The document under test's own {@code scheme_docno} — never the counter-document's. */
    @JsonProperty("BaseDocumentNumber")
    private String baseDocumentNumber;

    /**
     * The document under test's own actual amount — {@code finvchmain.camount} or
     * {@code damount}, whichever is non-zero for this voucher type (see
     * {@code DocumentAdjustmentFlow}'s Javadoc for how callers resolve this).
     */
    @JsonProperty("BaseDocumentActualAmount")
    private BigDecimal baseDocumentActualAmount;

    /** The document under test's own {@code finvchdet.slcode} — never the counter-document's. */
    @JsonProperty("BaseDocumentSLCode")
    private long baseDocumentSlCode;

    /**
     * Empty list clears every tag row on {@link #baseDocumentCode} in one call — the only shape
     * this framework's own untag flow ever sends (see class Javadoc). A real "adjust against
     * these specific documents" payload would populate this list, but no HAR capture exists for
     * that direction yet, so this field is carried only to complete the confirmed request shape.
     */
    @JsonProperty("AdjustedDocuments")
    @Builder.Default
    private List<Object> adjustedDocuments = List.of();
}
