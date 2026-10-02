package com.gsl.validation.snapshot.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpDao;
import com.gsl.database.dao.adm.AdmDocSchemeDao;
import com.gsl.database.dao.adm.AdmSiteDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.model.adm.AdmSite;
import com.gsl.model.finance.FinGl;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
import com.gsl.utils.dataprovider.source.finance.common.AccountingPeriodResolver;
import com.gsl.validation.snapshot.ColumnRule;
import com.gsl.validation.snapshot.DbValueNormalizer;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.ExpectedChangeSet;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link ExpectedChangeSet} builder for General Voucher's Create flow — the Create-side
 * counterpart to {@link GeneralVoucherEditChangeSets}, bringing Create-mode's DB validation up
 * to the same snapshot-diff-engine coverage Edit already has (unexpected-row detection +
 * {@code aud2_*} audit-trail INSERT checks), which {@link com.gsl.validation.db.finance.GeneralVoucherDbValidator}'s
 * hand-written "Level 1-4" checklist has never covered.
 *
 * <p>Unlike Edit, Create needs no BEFORE snapshot resolved from the database — the document
 * didn't exist before the save, so the caller passes a manufactured empty
 * {@link com.gsl.validation.snapshot.VoucherSnapshot} as BEFORE. {@link com.gsl.validation.snapshot.DiffEngine}
 * treats a missing/empty table as zero rows, so every row genuinely present in the AFTER
 * snapshot is, by construction, an INSERT — this class only has to declare which specific rows
 * are the EXPECTED inserts (so a row that appears with the wrong content, or an entirely
 * unrelated row, still fails as "unexpectedly appeared" rather than being silently accepted).
 *
 * <p>{@code finvchmain}/{@code finpost}(main-grid)/{@code fincosttag} use a permissive
 * {@code row -> true} insert matcher — safe here specifically because BEFORE is empty (so
 * "any row appearing" already means "newly inserted for this document"), NOT because their
 * content goes unchecked: {@link com.gsl.validation.db.finance.GeneralVoucherDbValidator}'s
 * existing Level 1/3/4 checks already assert their field content and VMG/VDG row-count
 * cardinality precisely — a check this engine has no generic vocabulary for (see the plan's
 * "keep GeneralVoucherDbValidator, run alongside" decision). {@code finvchdet} gets a precise
 * per-line matcher (glcode/slcode/amount/reversal/narration), reusing the exact matchers
 * {@link VoucherEditChangeSets}/{@link GeneralVoucherEditChangeSets} already use for the same
 * {@link ExpenseIncomeDetail} DTO, so a line landing with the wrong values is still caught as a
 * distinct failure rather than accepted as "just some insert".
 */
public final class GeneralVoucherCreateChangeSets {

    private GeneralVoucherCreateChangeSets() {}

    /**
     * Builds the full Create-mode {@link ExpectedChangeSet} — header/detail-line row inserts plus
     * every resolved exact-value/system-value column override described in this class's Javadoc.
     *
     * @param context resolved document context (site/user/cost-tag applicability, etc.)
     * @param data    the merged Create-flow test data (document header, detail lines, payment
     *                info, fixture options) this voucher was created from
     */
    public static ExpectedChangeSet build(DocumentContext context, MergedFinanceGeneralVoucherData data) {
        ExpectedChangeSet.Builder builder = ExpectedChangeSet.builder("GeneralVoucherCreateChangeSets.build");
        String mainEntType = context.getDocumentType().mainPostingEntType();

        // finvchmain — exactly one row per document; BEFORE is empty, so any row appearing here is
        // by definition new. See class Javadoc for why "row -> true" doesn't mean unchecked.
        builder.expectRowInsert("finvchmain", row -> true);

        // Item Z (Round 5): declare the genuinely user-supplied header columns as MUST_CHANGE
        // (mirrors VoucherEditChangeSets' own treatment of the same columns for Edit) so DiffEngine's
        // INSERT-explode loop resolves a real override for them instead of falling through to the
        // generic "EXPECTED INSERT" -> CASCADE_CHANGE label — without this, no Create column can
        // ever classify as USER_CHANGE. mustChange (NOT_EQUAL against a null "before") only holds
        // for a column that actually has a non-blank value on this row, so each is gated on the
        // corresponding test-data input actually having been supplied — an always-blank Reference
        // must stay off the table's normal default, not get a bogus MUST_CHANGE(null, null) failure.
        var docInfo = data.getDocumentInformation();
        if (docInfo != null && docInfo.getReference() != null && !docInfo.getReference().isBlank()) {
            builder.rule("finvchmain", ColumnRule.mustChange("refno"));
        }
        if (docInfo != null && docInfo.getRemarks() != null && !docInfo.getRemarks().isBlank()) {
            builder.rule("finvchmain", ColumnRule.mustChange("nartext"));
        }

        // Round 6: vchdt/glcode are, like refno/nartext, genuinely always supplied via the UI for
        // every Create scenario (Document Date and Cash/Bank GL are never optional fields, unlike
        // Reference/Remarks) — unconditional MUST_CHANGE, classifying as USER_CHANGE/"From UI"
        // instead of the generic cascade label.
        builder.rule("finvchmain", ColumnRule.mustChange("vchdt"));
        builder.rule("finvchmain", ColumnRule.mustChange("glcode"));

        // Round 6: system-resolved header values — each looked up ONCE here at changeset-build
        // time and asserted via SYSTEM_CHANGE (not MUST_CHANGE — none of these are something the
        // user directly types; they're derived from who's logged in / what was selected, same
        // treatment VoucherEditChangeSets already gives last_access_ecode).
        Jdbi jdbi = DatabaseManager.getJdbi();

        Long resolvedEcode = jdbi.onDemand(HrEmpDao.class).findEcodeByAbbre(context.getCurrentUser()).orElse(null);
        if (resolvedEcode != null) {
            builder.rule("finvchmain", ColumnRule.systemChangeTo("ecode", resolvedEcode));
            // Round 7: finpost.ecode is the same "logged-in user" column/meaning as
            // finvchmain.ecode — reuses the SAME resolved value, no new DB call.
            builder.rule("finpost", ColumnRule.systemChangeTo("ecode", resolvedEcode));
        } else {
            Log.warn("[GeneralVoucherCreateChangeSets] Could not resolve ecode for user '{}' — "
                    + "finvchmain.ecode keeps its table-wide mandatory-not-null check only, no exact-value assertion.",
                    context.getCurrentUser());
        }

        // Hoisted to method scope (Round 7) — fincosttag's own rules further below reuse these
        // same resolved values too (same "no new DB call" reasoning as finpost's above).
        Long resolvedSiteCode = null;
        Long resolvedOuCode = null;

        String ownerSiteName = docInfo != null ? docInfo.getOwnersite() : null;
        if (ownerSiteName != null && !ownerSiteName.isBlank()) {
            List<AdmSite> matches = jdbi.onDemand(AdmSiteDao.class).findByName(ownerSiteName);
            if (!matches.isEmpty()) {
                resolvedSiteCode = matches.get(0).getCode();
                builder.rule("finvchmain", ColumnRule.mustChangeTo("admsite_code_owner", resolvedSiteCode));
                // Round 7: finpost carries the identical column/meaning — reuses the SAME
                // resolved site code, no new DB call.
                builder.rule("finpost", ColumnRule.mustChangeTo("admsite_code_owner", resolvedSiteCode));
                try {
                    resolvedOuCode = AccountingPeriodResolver.resolveOuCode(jdbi, resolvedSiteCode);
                    builder.rule("finvchmain", ColumnRule.systemChangeTo("admou_code", resolvedOuCode));
                    builder.rule("finpost", ColumnRule.systemChangeTo("admou_code", resolvedOuCode));
                } catch (IllegalStateException e) {
                    Log.warn("[GeneralVoucherCreateChangeSets] Could not resolve admou_code for owner site '{}' "
                            + "(code={}): {}", ownerSiteName, resolvedSiteCode, e.getMessage());
                }
            } else {
                Log.warn("[GeneralVoucherCreateChangeSets] Could not resolve admsite row for Owner Site '{}' — "
                        + "finvchmain.admsite_code_owner/admou_code keep their generic checks only.", ownerSiteName);
            }
        }

        Long resolvedDoccode = jdbi.onDemand(AdmDocSchemeDao.class).findDoccodeByDocname("General Voucher");
        if (resolvedDoccode != null) {
            builder.rule("finvchmain", ColumnRule.systemChangeTo("doccode", resolvedDoccode));
        } else {
            Log.warn("[GeneralVoucherCreateChangeSets] Could not resolve doccode for docname 'General Voucher' "
                    + "— finvchmain.doccode goes unchecked this run.");
        }

        // Round 6: GV-only invariants — deliberately NOT added to the shared
        // VoucherPersistenceMapFactory.finvchmain() table rule, since both would be wrong for
        // other voucher domains sharing this same physical table (AP legitimately populates
        // isadvance; AR/AP's own release_status domain isn't confirmed identical).
        builder.rule("finvchmain", ColumnRule.mandatoryNull("isadvance"));
        builder.rule("finvchmain", ColumnRule.mandatoryOneOf("release_status", "P", "U"));

        // Round 7: finvchdet.vchdt/vchtype — table-wide (every line shares the header's own
        // date/voucher-type), asserted against the SAME already-known values used for
        // finvchmain's own vchdt/vchtype, not a cross-table read.
        if (docInfo != null && docInfo.getDocumentdate() != null) {
            builder.rule("finvchdet", ColumnRule.mustChangeTo("vchdt", docInfo.getDocumentdate()));
            builder.rule("finpost", ColumnRule.mustChangeTo("entdt", docInfo.getDocumentdate()));
        }
        builder.rule("finvchdet", ColumnRule.mustChangeTo("vchtype", context.getDocumentType().code()));

        // Round 9 Item B — completes a Round 7 gap: these were in the original plan text but the
        // actual rule declarations were never added (only finpost's equivalents were). "will be
        // blank in general voucher; this value comes in AR and AP Voucher" — adjamt mirrors
        // finpost.adjamt's confirmed-live "literal 0, not NULL" behavior (mandatoryZero); TDS is
        // an AP-Voucher-advance concept (confirmed scope decision) — all 11 finvchdet TDS columns,
        // GV-scoped, mandatoryNull. tds_app already has its own dedicated field/treatment
        // elsewhere (VoucherEditChangeSets' TDS_APP_NOTE knownIssue for Edit) — not duplicated here.
        builder.rule("finvchdet", ColumnRule.mandatoryZero("adjamt"));
        builder.rule("finvchdet", ColumnRule.mandatoryNull("ref_admsite_code"));
        for (String tdsColumn : List.of("tds_rate", "tdstypecode", "tdssrccode", "tdssection",
                "tdsglcode", "tdsslcode", "tds_appamt", "tdsamt", "tds_grossamt",
                "tds_roundoff_amt", "grs_amt")) {
            builder.rule("finvchdet", ColumnRule.mandatoryNull(tdsColumn));
        }

        // Round 7 — finpost full column-by-column pass (by analogy to finvchdet's user-specified
        // spec; see plan's "Design — finpost"). Reuse-as-is columns (entcode/enttype/glcode/
        // damount/camount) are already table-wide mandatoryNotNull in VoucherPersistenceMapFactory.
        if (docInfo != null && docInfo.getReference() != null && !docInfo.getReference().isBlank()) {
            builder.rule("finpost", "enttype=" + mainEntType, ColumnRule.mustChangeTo("docno", docInfo.getReference()));
        }
        if (docInfo != null && docInfo.getRemarks() != null && !docInfo.getRemarks().isBlank()) {
            builder.rule("finpost", "enttype=" + mainEntType, ColumnRule.mustChangeTo("nartext", docInfo.getRemarks()));
        }
        builder.rule("finpost", ColumnRule.mandatoryOneOf("release_status", "P", "U"));
        // "will be blank in general voucher; this value comes in AR and AP Voucher" — same
        // reasoning as finvchdet.adjamt/ref_admsite_code, GV-scoped (not table-wide). Round 9 Item
        // A: adjamt is confirmed live to store a literal 0 (BigDecimal), not SQL NULL, for GV —
        // mandatoryZero, not mandatoryNull (which always failed against the real value).
        builder.rule("finpost", ColumnRule.mandatoryZero("adjamt"));
        builder.rule("finpost", ColumnRule.mandatoryNull("ref_admsite_code"));
        // TDS is an AP-Voucher-advance concept (confirmed scope decision) — finpost's own 5 TDS
        // columns (a smaller subset than finvchdet's, no tdsglcode/tdsslcode/tdsamt/tds_grossamt/
        // tds_roundoff_amt/grs_amt here).
        for (String tdsColumn : List.of("tdstypecode", "tdssrccode", "tdssection", "tdsrate", "tds_appamt")) {
            builder.rule("finpost", ColumnRule.mandatoryNull(tdsColumn));
        }
        // Inferred by analogy — flagged for confirmation (no direct user-given precedent for
        // these on finpost; deferred columns, left as mandatoryNull until confirmed otherwise).
        builder.rule("finpost", ColumnRule.mandatoryNull("certificateno"));
        builder.rule("finpost", ColumnRule.mandatoryNull("servicecode"));
        for (String chargeColumn : List.of("chgcode", "chgrate", "chgappamt", "chgamt", "chg_pk_code")) {
            builder.rule("finpost", ColumnRule.mandatoryNull(chargeColumn));
        }
        builder.rule("finpost", ColumnRule.mandatoryNull("docdt"));
        builder.rule("finpost", ColumnRule.mandatoryNull("duedt"));
        // Confirmed (not inferred): cash-discount-applicable entries are an AP Voucher concept.
        // cash_discount_applicable is NOT NULL in the live schema (a flag column, not a truly
        // nullable one) — asserted as an exact "N" (not applicable), not blank; the other two
        // (percentage/period) are genuinely nullable numeric/int columns.
        builder.rule("finpost", ColumnRule.mustChangeTo("cash_discount_applicable", "N"));
        builder.rule("finpost", ColumnRule.mandatoryNull("cash_discount_percentage"));
        builder.rule("finpost", ColumnRule.mandatoryNull("cash_discount_period"));
        // Round 23 (confirmed, not inferred): intg_code is populated only for a PUBLICAPI-created
        // entry — WEBAPI hits the SAME internal endpoint the web UI itself calls (per
        // ApiPlatform.java's own Javadoc), so finvchmain.entry_source is "WEB" for both a real
        // browser click AND WEBAPI-driven test data; "API" is reserved specifically for PUBLICAPI
        // (see FinVchMainBaseValidator's own WEB|API check). Verified this changeset's own voucher
        // (the one under test) is never itself PUBLICAPI-created: GeneralVoucherApiClient exists,
        // but its only caller is GeneralVoucherApiBackfillStrategy — used to seed OTHER, unrelated
        // test data, not to create the GV document this changeset validates, which is exclusively
        // UI-driven (no test under src/test references GeneralVoucherApiClient directly). So
        // entry_source is unconditionally "WEB" here and intg_code is correctly expected blank —
        // unlike FinPostValidator's shared Level 3 check, this changeset has no access to an
        // execution-channel signal at build time even if it needed one (DocumentContext/
        // GeneralVoucherFixtureOptions carry no such field today), so a genuinely conditional
        // version of this rule isn't possible here yet regardless. Should a Create changeset ever
        // be built for a domain that DOES create its own tested document via PUBLICAPI (AR, via
        // ArVoucherApiClient — no such changeset exists today), that class would need the
        // conditional version, mirroring Level 3's check exactly.
        builder.rule("finpost", ColumnRule.mandatoryNull("intg_code"));

        // finpost — main-grid (VMG/VMP) row. Same "empty BEFORE" reasoning as finvchmain.
        builder.expectRowInsert("finpost", row -> mainEntType.equalsIgnoreCase(
                String.valueOf(DbValueNormalizer.getIgnoreCase(row, "enttype"))));

        // finvchdet — one row per Expense/Income Detail line, matched by (glcode, slcode) AND the
        // expected amount/reversal/narration (reusing VoucherEditChangeSets' matchers — see class
        // Javadoc). Each line also declares its matching finpost per-line (VDG/VDP) row insert,
        // mirroring VoucherEditChangeSets#addNewDetailLine's own shape for an Edit-time new line.
        FinGlDao glDao = DatabaseManager.getJdbi().onDemand(FinGlDao.class);
        List<ExpenseIncomeDetail> lines = data.getExpenseIncomeDetailList();
        boolean chequeBookRulesAdded = false;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            ExpenseIncomeDetail line = lines.get(lineIndex);
            FinGl gl = glDao.findByGlName(line.getGeneralLedger());
            if (gl == null) {
                Log.warn("[GeneralVoucherCreateChangeSets] Could not resolve fingl row for General Ledger '{}' "
                        + "— that finvchdet/finpost row's precise insert-match override was NOT added; if it "
                        + "genuinely inserted, it will surface as an 'unexpectedly appeared' failure instead of "
                        + "a clean, attributable pass.", line.getGeneralLedger());
                continue;
            }

            boolean isDebit = line.isDebit();
            BigDecimal amount = isDebit ? line.getExpenseDr() : line.getIncomeCr();
            long slcode = GeneralVoucherEditChangeSets.resolveSlcode(line);
            String amountColumn = isDebit ? "damount" : "camount";
            String expectedIsReversal = line.isReversalYes() ? "Y" : "N";
            String narration = line.getLedgerNarration();
            long glcode = gl.getGlcode();

            builder.expectRowInsert("finvchdet", row -> VoucherEditChangeSets.matchesKey(row, glcode, slcode)
                    && VoucherEditChangeSets.amountMatches(row, amountColumn, amount)
                    && VoucherEditChangeSets.reversalMatches(row, expectedIsReversal)
                    && (narration == null || VoucherEditChangeSets.narrationMatches(row, narration)));

            builder.expectRowInsert("finpost", row -> VoucherEditChangeSets.notMainEntType(row, mainEntType)
                    && VoucherEditChangeSets.matchesKey(row, glcode, slcode)
                    && VoucherEditChangeSets.amountMatches(row, amountColumn, amount));

            // Item Z (Round 5): per-line user-supplied columns as MUST_CHANGE, row-scoped to this
            // line's (glcode, slcode) — same rowKeyContains shape VoucherEditChangeSets.detailLineEdit
            // already uses for Edit's equivalent per-line overrides. Amount and isreversal are always
            // present on every line (never legitimately blank), so unconditional; nartext is gated on
            // the line's own Ledger Narration actually having been supplied, same reasoning as the
            // header columns above.
            String rowKeyContains = "glcode=" + glcode + ", slcode=" + VoucherEditChangeSets.slcodeDisplay(slcode);
            builder.rule("finvchdet", rowKeyContains, ColumnRule.mustChange(amountColumn));
            builder.rule("finvchdet", rowKeyContains, ColumnRule.mustChange("isreversal"));
            if (narration != null && !narration.isBlank()) {
                builder.rule("finvchdet", rowKeyContains, ColumnRule.mustChange("nartext"));
                // Round 7: finpost's per-line (VDG) row carries its own, separate nartext —
                // mirrors VoucherEditChangeSets.addFinpostLineNarrationRule's Edit-time treatment,
                // same "glcode and slcode basis" scoping the user gave for finvchdet.nartext.
                builder.rule("finpost", rowKeyContains, ColumnRule.mustChangeTo("nartext", narration));
            }

            // Round 7 — corrected gap: gl_cc_appl's actual VALUE was never asserted, only its
            // non-null-ness. "If gl ledger is site applicable then gl_cc_appl will be Y else N" —
            // reuses the SAME FinGl already resolved above, no new DB call.
            builder.rule("finvchdet", rowKeyContains,
                    ColumnRule.mustChangeTo("gl_cc_appl", gl.isSiteApplicable() ? "Y" : "N"));

            // Round 7/Round 6 — payment_mode/chqno/chqdt/drawnon/bookcode live on EACH Expense/Income
            // Detail row (finvchdet is per row), so every row is checked against ITS OWN payment
            // variant and payment — the row-scoped rule (glcode, slcode) keeps rows from mixing up.
            if (data.getFixtureOptions() != null) {
                String expectedPaymentModeCode = data.getFixtureOptions().paymentForRow(lineIndex).expectedDbPaymentModeCode();
                if (expectedPaymentModeCode != null) {
                    builder.rule("finvchdet", rowKeyContains, ColumnRule.mustChangeTo("payment_mode", expectedPaymentModeCode));
                }
                PaymentInformation paymentInfo = data.paymentForLine(line.getLineNo());
                if (paymentInfo != null) {
                    if (paymentInfo.getInstrumentNo() != null && !paymentInfo.getInstrumentNo().isBlank()) {
                        builder.rule("finvchdet", rowKeyContains, ColumnRule.mustChangeTo("chqno", paymentInfo.getInstrumentNo()));
                    }
                    if (paymentInfo.getInstrumentDate() != null && !paymentInfo.getInstrumentDate().isBlank()) {
                        // Only asserted as "changed to something" — the test-data value is a
                        // UI-formatted display string (dd/MM/yyyy), not directly comparable to the
                        // DB's raw timestamp value via EXPECTED_VALUE.
                        builder.rule("finvchdet", rowKeyContains, ColumnRule.mustChange("chqdt"));
                    }
                    if (paymentInfo.getDrawnOn() != null && !paymentInfo.getDrawnOn().isBlank()) {
                        builder.rule("finvchdet", rowKeyContains, ColumnRule.mustChangeTo("drawnon", paymentInfo.getDrawnOn()));
                    }
                }
                // Round 23: read the resolver's ACTUAL outcome for THIS row (not the Fixture Factory's
                // pre-decided known intent) — a "known" row whose resolver silently fell back to unknown
                // (no available registered cheque for that GL) must NOT expect a bookcode. bookcode is
                // populated only for a genuine SAME_BANK/DIFFERENT_BANK resolution; UNKNOWN and every
                // non-Cheque payment mode (match stays null) must stay blank.
                //
                // Dr/Cr bifurcation: a Cr-side (Receipt) line behaves AR-style — no company cheque-book
                // concept at all — so it must NEVER expect a bookcode (confirmed live in idealdb:
                // VG/02556/26-27 Dr has bookcode + a finchqdet row; VG/02597/26-27 Cr has neither).
                ChequeInstrumentResolver.ChequeBankMatch rowMatch = data.chequeBankMatchForLine(line.getLineNo());
                boolean expectBookcode = isDebit
                        && rowMatch != null
                        && rowMatch != ChequeInstrumentResolver.ChequeBankMatch.UNKNOWN;
                builder.rule("finvchdet", rowKeyContains, expectBookcode
                        ? ColumnRule.mandatoryNotNull("bookcode")
                        : ColumnRule.mandatoryNull("bookcode"));

                // Round 23: a genuinely KNOWN/registered cheque was resolved — finchqdet/finchqmain
                // participate in the snapshot; the cheque leaf goes 'N' -> 'I' and is linked to this
                // voucher. Declared once however many rows resolve one (the rule is table-wide).
                if (expectBookcode && !chequeBookRulesAdded) {
                    chequeBookRulesAdded = true;
                    builder.rule("finchqdet", ColumnRule.mustChangeTo("stat", "I"));
                    builder.rule("finchqdet", ColumnRule.mustChange("vchcode"));
                }
            }
        }

        // fincosttag — participates only when the document has a site-applicable Income/Expense
        // line (resolved live post-save by VoucherDocumentContextResolver into
        // context.isCostTagApplicable()). "row -> true" is safe for the same "empty BEFORE"
        // reasoning as finvchmain/finpost above; precise site-allocation-vs-amount content is
        // independently checked by GeneralVoucherDbValidator's Level 4 (FinCostTagValidator).
        if (context.isCostTagApplicable()) {
            builder.expectRowInsert("fincosttag", row -> true);

            // Round 7 — full column-by-column pass for fincosttag (extends Round 3 Item G's
            // mandatoryNotNull-only coverage with resolved exact-value checks).
            //
            // Round 13 — bug fix: Round 7's blanket `mustChangeTo("admsite_code", resolvedSiteCode)`
            // was REMOVED here — it was flat-out wrong, not just imprecise. `admsite_code` is this
            // ROW's own allocated site (the same per-SiteAllocation value `ref_admsite_code` holds
            // — confirmed live: entcode=625502 has two fincosttag rows, code=174502 with
            // admsite_code=ref_admsite_code=1 (glcode=1497, happens to match the Owner Site) and
            // code=174501 with admsite_code=ref_admsite_code=1106 (glcode=1759/slcode=334, a
            // DIFFERENT site entirely) — NOT a constant "echo of the header's Owner Site" repeated
            // on every row. A table-wide/non-row-scoped rule asserting one fixed `resolvedSiteCode`
            // (the Owner Site) can therefore only ever be coincidentally right and is guaranteed to
            // false-fail the moment any line's own site allocation differs from the Owner Site —
            // exactly the reported MIXED_TYPES failure (expected=1, actual=1106), and exactly the
            // "site applicable is different and multiple ledgers have data for the same voucher"
            // scenario originally reported. This is a DIFFERENT bug from Round 10's fix (which
            // rescoped FinCostTagValidator's row lookup from (entcode, glcode) to postcode, fixing
            // a SUM-pooling false failure in the hand-written Level 4 check) — that fix never
            // touched this DiffEngine/changeset-level rule at all, which is why the same class of
            // symptom kept recurring after it. A per-row exact-value fix isn't possible here either
            // (same limitation already noted below for ref_admsite_code): fincosttag's business key
            // is the surrogate `code`, unknown at changeset-build time, so this column can't be
            // row-scoped by glcode/slcode the way finvchdet/finpost's per-line rules are. Real
            // per-allocation site+amount correctness is already fully enforced by
            // FinCostTagValidator's Level 4 check (validate(...), matches each expected
            // SiteAllocation's site+amount against the postcode-scoped rows) — this column is left
            // on the table's existing mandatoryNotNull-only check (Round 3 Item G), same treatment
            // ref_admsite_code already correctly has.
            if (resolvedOuCode != null) {
                builder.rule("fincosttag", ColumnRule.systemChangeTo("admou_code", resolvedOuCode));
            }
            if (docInfo != null && docInfo.getDocumentdate() != null) {
                // Confirmed (not inferred): "entdt will be same as document date value."
                builder.rule("fincosttag", ColumnRule.mustChangeTo("entdt", docInfo.getDocumentdate()));
            }
            // Confirmed live against finenttype (VDG registered, entname='Voucher',
            // entgrpcode='1', prefix='VG ') — General Voucher detail-line site allocations.
            builder.rule("fincosttag", ColumnRule.mustChangeTo("enttype", "VDG"));
            // entno — Round 9 Item A: confirmed live (code=174476) this is genuinely nullable for
            // GV — the earlier tentative mandatoryNotNull always failed against real data. Left
            // unchecked, matching fincosttag.postcode's own "no plausible value to assert" note.
            // admsite_code/ref_admsite_code — see the Round 13 note above (both left on the
            // table's mandatoryNotNull-only check, no exact-value rule possible here).
        }

        return builder.build();
    }
}
