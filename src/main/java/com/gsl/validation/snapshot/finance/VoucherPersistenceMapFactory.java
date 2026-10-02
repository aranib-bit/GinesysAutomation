package com.gsl.validation.snapshot.finance;

import com.gsl.enums.VoucherDomain;
import com.gsl.validation.snapshot.ChangeExpectation;
import com.gsl.validation.snapshot.ColumnRule;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.RowMatchStrategy;
import com.gsl.validation.snapshot.TableRule;
import com.gsl.validation.snapshot.VoucherPersistenceMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link VoucherPersistenceMap} for any voucher domain — plan §2, generalized from
 * the GV-only {@code GeneralVoucherPersistenceMap} it replaces.
 *
 * <p>{@code finvchmain}/{@code finvchdet}/{@code finpost} are the literal same physical tables
 * across AR/AP/GV (discriminated only by {@code finvchmain.vchtype} — see
 * {@link VoucherDomain#getVoucherType()}), so the common base below applies unchanged to every
 * domain. {@code fincosttag} is common too, not a GV-only addition — its participation condition
 * ({@code finvchdet.gl_cc_appl='Y'} on any line, resolved into
 * {@link DocumentContext#isCostTagApplicable()}) and table/key shape are domain-agnostic; the
 * {@code aud2_active_table} registry itself files {@code FINCOSTTAG} under module
 * {@code "Finance Site Allocation"}, not {@code "FinVoucher"} — independent confirmation this is
 * cross-voucher-type, not GV's own.
 *
 * <p>A domain layers its own genuinely-extra tables via
 * {@link VoucherDomain#additionalPersistenceTables()}, and can replace one of the common base's
 * own {@link TableRule}s via {@link VoucherDomain#overridePersistenceTableRule(String)} (e.g. a
 * wider {@code finvchdet} business key for a domain with sub-ledger columns) — neither hook is
 * used by any domain today (GV included, now that {@code fincosttag} moved here), pure
 * future-proofing until a real per-domain need is found.
 */
public final class VoucherPersistenceMapFactory {

    private VoucherPersistenceMapFactory() {}

    /**
     * Assembles {@code domain}'s full {@link VoucherPersistenceMap} — the common base table rules
     * (optionally replaced per-table via {@link VoucherDomain#overridePersistenceTableRule(String)})
     * plus {@code domain}'s own {@link VoucherDomain#additionalPersistenceTables()}.
     */
    public static VoucherPersistenceMap build(VoucherDomain domain) {
        TableRule finvchmain = finvchmain();
        TableRule finvchdet = finvchdet();
        TableRule finpost = finpost();
        TableRule fincosttag = fincosttag();
        TableRule finchqdet = finchqdet();
        TableRule finchqmain = finchqmain();

        List<TableRule> base = List.of(
                finvchmain,
                finvchdet,
                finpost,
                fincosttag,
                finchqdet,
                finchqmain,
                auditTable("aud2_finvchmain", finvchmain),
                auditTable("aud2_finvchdet", finvchdet),
                auditTable("aud2_finpost", finpost),
                auditTable("aud2_fincosttag", fincosttag)
                // aud2_finchqdet/aud2_finchqmain deliberately NOT added — unconfirmed whether
                // these audit-shadow tables are even registered in this app's aud2_active_table
                // registry (cheque-book master/detail is reference/master data, not necessarily
                // audit-tracked the way voucher tables are). Add via the same auditTable(...)
                // helper once confirmed; omitting cleanly costs nothing today since Pass 2 of
                // DiffEngine simply never runs for a table not in this list.
        );

        List<TableRule> resolved = new ArrayList<>(base.size());
        for (TableRule rule : base) {
            resolved.add(domain.overridePersistenceTableRule(rule.getTableName()).orElse(rule));
        }
        resolved.addAll(domain.additionalPersistenceTables());

        return new VoucherPersistenceMap(resolved);
    }

    /** {@code finvchmain} — key {@code [vchcode]}, always participates (mirrors
     *  {@code FinVchMainGvValidator}'s Level 1 fetch, keyed by {@code vchcode}). */
    private static TableRule finvchmain() {
        return TableRule.builder()
                .tableName("finvchmain")
                .businessKeyColumn("vchcode")
                .fkColumn("vchcode")
                .fkValueResolver(ctx -> ctx.getVchcode())
                .rowMatchStrategy(RowMatchStrategy.BY_KEY)
                .defaultRule(ChangeExpectation.MUST_NOT_CHANGE)
                // Item G — mandatory-not-null columns for a freshly-inserted/deleted row (confirmed
                // real, non-nullable-by-design fields on FinVchMain.java): a null here on Create is
                // a genuine data-integrity bug, not a display nuance. Round 23 correction: this is
                // NOT scoped to DiffEngine's INSERT/DELETE row-explode path alone — a table-wide
                // `columnRule` override like this one wins over the table's MUST_NOT_CHANGE default
                // in DiffEngine.resolveColumnRule's priority order for EVERY BY_KEY UPDATE match
                // too (Edit, Revert, ...), not just INSERT/DELETE. A scenario changeset that wants
                // "unchanged" instead of "not-null" for one of these columns (e.g.
                // GeneralVoucherRevertChangeSets) must add its own explicit MUST_NOT_CHANGE
                // override, since the table-wide default here is otherwise silently bypassed.
                .columnRule(ColumnRule.mandatoryNotNull("ycode"))
                .columnRule(ColumnRule.mandatoryNotNull("vchtype"))
                .columnRule(ColumnRule.mandatoryNotNull("entry_source"))
                .columnRule(ColumnRule.mandatoryNotNull("admsite_code_owner"))
                .columnRule(ColumnRule.mandatoryNotNull("ecode"))
                .columnRule(ColumnRule.mandatoryNotNull("creation_dt"))
                // Round 6: 4 more genuinely universal invariants for ANY voucher domain's
                // finvchmain row — table-wide, same home as the 6 above. vchcode is the PK;
                // scheme_docno/dataversion/time are confirmed (user spec) to never be blank on
                // any real row, regardless of domain.
                .columnRule(ColumnRule.mandatoryNotNull("vchcode"))
                .columnRule(ColumnRule.mandatoryNotNull("scheme_docno"))
                .columnRule(ColumnRule.mandatoryNotNull("dataversion"))
                .columnRule(ColumnRule.mandatoryNotNull("time"))
                // Item H (Round 9): vchdt/doccode/glcode were previously only asserted via a
                // GV-scoped changeset rule (GeneralVoucherCreateChangeSets) — meaning an
                // Excel-import or public-API-created voucher, which never runs through that
                // changeset, got no not-null guarantee at all. Table-wide here since these are
                // universal invariants regardless of domain or creation channel.
                .columnRule(ColumnRule.mandatoryNotNull("vchdt"))
                .columnRule(ColumnRule.mandatoryNotNull("doccode"))
                .columnRule(ColumnRule.mandatoryNotNull("glcode"))
                .build();
    }

    /** {@code finvchdet} — key {@code [vchcode, glcode, slcode]}. {@code slcode} is required,
     *  not just {@code (vchcode, glcode)}: two Type3 (sub-ledger-applicable) lines can legitimately
     *  share a {@code glcode} with different {@code slcode} — confirmed live (two real rows on the
     *  same document collided into one business key before this fix, silently dropping one line
     *  from validation). <b>Open item resolved</b>: whether an edited row is updated in place or
     *  delete+reinserted turns out not to matter here — {@code vchcode}/{@code glcode}/
     *  {@code slcode} are stable business columns, not a recreated surrogate id, so
     *  {@code BY_KEY} matches correctly either way. {@link #finpost()} now follows the exact same
     *  principle — its key is built from stable business columns
     *  ({@code entcode}/{@code glcode}/{@code slcode}/{@code enttype}), deliberately excluding the
     *  {@code postcode} surrogate, so the same "doesn't matter either way" reasoning applies
     *  there too. */
    private static TableRule finvchdet() {
        return TableRule.builder()
                .tableName("finvchdet")
                .businessKeyColumn("vchcode")
                .businessKeyColumn("glcode")
                .businessKeyColumn("slcode")
                .fkColumn("vchcode")
                .fkValueResolver(ctx -> ctx.getVchcode())
                .rowMatchStrategy(RowMatchStrategy.BY_KEY)
                .defaultRule(ChangeExpectation.MUST_NOT_CHANGE)
                // Item G — mandatory-not-null columns for a freshly-inserted/deleted row (confirmed
                // real fields on FinVchDet.java). damount/camount: double-entry means the unused
                // side is a legitimate 0.00, never a genuine NULL.
                .columnRule(ColumnRule.mandatoryNotNull("vchcode"))
                .columnRule(ColumnRule.mandatoryNotNull("glcode"))
                .columnRule(ColumnRule.mandatoryNotNull("damount"))
                .columnRule(ColumnRule.mandatoryNotNull("camount"))
                .columnRule(ColumnRule.mandatoryNotNull("isreversal"))
                .columnRule(ColumnRule.mandatoryNotNull("gl_cc_appl"))
                // Round 7: genuinely universal invariants for ANY voucher domain's finvchdet row
                // (confirmed live against idealdb's 37-column schema) — "ycode same logic as
                // finvchmain" (plain not-null) and "dataversion must be present" per the user's
                // own spec; brsdt/printchq are the opposite direction (must STAY blank at
                // creation time — reconciled/printed only by their own later-step UI menus).
                .columnRule(ColumnRule.mandatoryNotNull("ycode"))
                .columnRule(ColumnRule.mandatoryNotNull("dataversion"))
                .columnRule(ColumnRule.mandatoryNull("brsdt"))
                .columnRule(ColumnRule.mandatoryNull("printchq"))
                .build();
    }

    /** {@code finpost} — key {@code [entcode, glcode, slcode, enttype]}, deliberately mirroring
     *  {@code finvchdet()}'s own key shape: that table's key is its document-scoping fk column
     *  ({@code vchcode}) PLUS {@code glcode}/{@code slcode}, not {@code glcode}/{@code slcode}
     *  alone — {@code finpost}'s document-scoping fk column is {@code entcode} (the varchar
     *  mirror of {@code vchcode} this table's own {@code fkColumn}/{@code fkValueResolver} already
     *  bind), so the analogous key includes it too. {@code enttype} distinguishes the header-level
     *  "main grid" posting row (e.g. {@code "enttype=VMG"} for GV, {@code "enttype=VMP"} for AR/AP
     *  — see {@code VoucherType#mainPostingEntType()}) from per-line "VDG"/"VDP" rows.
     *
     *  <p>{@code entcode} is constant within one document's captured snapshot (every fetched row
     *  already shares it — the capture query itself is {@code WHERE entcode = ctx.getVchcode()}),
     *  so on its own it doesn't discriminate rows any more than {@code vchcode} does in
     *  {@code finvchdet()}'s key — it's included purely for structural consistency with that
     *  established pattern. The columns that actually discriminate rows within one document are
     *  {@code glcode}+{@code slcode}+{@code enttype}: the header row has a unique {@code glcode}
     *  (the Cash/Bank leg) with null {@code slcode}; each per-line row corresponds 1:1 to one
     *  {@code finvchdet} line, whose own key is already {@code [vchcode, glcode, slcode]} — so
     *  {@code (glcode, slcode, enttype='VDG'/'VDP')} is unique per document by the same reasoning
     *  already proven there. {@code postcode} deliberately does NOT appear in this key — it's a
     *  per-row surrogate with no business meaning (confirmed live: two real per-line rows on the
     *  same document shared {@code glcode=1218} with different {@code slcode} (342/343) and
     *  {@code postcode} (587273/587274) — {@code postcode} couldn't have disambiguated them any
     *  more than {@code glcode} alone could); it remains a normal tracked column (still
     *  {@code MUST_NOT_CHANGE} by default), just not part of row identity. */
    private static TableRule finpost() {
        return TableRule.builder()
                .tableName("finpost")
                .businessKeyColumn("entcode")
                .businessKeyColumn("glcode")
                .businessKeyColumn("slcode")
                .businessKeyColumn("enttype")
                .fkColumn("entcode")
                .fkValueResolver(ctx -> String.valueOf(ctx.getVchcode()))
                .rowMatchStrategy(RowMatchStrategy.BY_KEY)
                .defaultRule(ChangeExpectation.MUST_NOT_CHANGE)
                // Item G — mandatory-not-null columns for a freshly-inserted/deleted row (confirmed
                // real fields on FinPost.java). damount/camount: same both-sides-non-null reasoning
                // as finvchdet — the unused side is a legitimate 0.00, never a genuine NULL.
                .columnRule(ColumnRule.mandatoryNotNull("entcode"))
                .columnRule(ColumnRule.mandatoryNotNull("enttype"))
                .columnRule(ColumnRule.mandatoryNotNull("glcode"))
                .columnRule(ColumnRule.mandatoryNotNull("damount"))
                .columnRule(ColumnRule.mandatoryNotNull("camount"))
                // Round 7: mirrors finvchmain's own treatment of the identical column names —
                // genuinely universal invariants for ANY voucher domain's finpost row.
                .columnRule(ColumnRule.mandatoryNotNull("ycode"))
                .columnRule(ColumnRule.mandatoryNotNull("scheme_docno"))
                .columnRule(ColumnRule.mandatoryNotNull("time"))
                .build();
    }

    /** {@code fincosttag} — key {@code [code]}; participates only when the document has at least
     *  one {@code finvchdet} line that is <b>both</b> Site-applicable ({@code gl_cc_appl='Y'})
     *  <b>and</b> posted to an Income or Expense ledger ({@code fingl.type} — a Site-applicable
     *  Asset/Liability line does not participate) — resolved live by
     *  {@code VoucherDocumentContextResolver} into {@link DocumentContext#isCostTagApplicable()},
     *  not inferred from test data. Common to every domain (see class Javadoc), not GV-specific.
     *  {@code identityColumn("entcode")}: a Save recreates this row under a new surrogate
     *  {@code code} value whenever cost-tag is applicable (see {@code VoucherEditChangeSets}'
     *  {@code expectRowDelete}/{@code expectRowInsert} pair) — {@code entcode} (the parent
     *  document's own key) is the stable identity across that delete+reinsert, letting
     *  {@code AuditChangeReconstructor} reconcile the DELETE+INSERT audit pair into one business
     *  UPDATE instead of reporting them as two unrelated row-level changes. */
    private static TableRule fincosttag() {
        return TableRule.builder()
                .tableName("fincosttag")
                .businessKeyColumn("code")
                .identityColumn("entcode")
                .fkColumn("entcode")
                .fkValueResolver(ctx -> String.valueOf(ctx.getVchcode()))
                .participationCondition(DocumentContext::isCostTagApplicable)
                .rowMatchStrategy(RowMatchStrategy.BY_KEY)
                .defaultRule(ChangeExpectation.MUST_NOT_CHANGE)
                // Item G — mandatory-not-null columns for a freshly-inserted/deleted row (confirmed
                // real fields on FinCostTag.java). slcode is deliberately excluded — genuinely
                // nullable (Type1/2 rows have no Sub Ledger).
                .columnRule(ColumnRule.mandatoryNotNull("entcode"))
                .columnRule(ColumnRule.mandatoryNotNull("enttype"))
                .columnRule(ColumnRule.mandatoryNotNull("glcode"))
                .columnRule(ColumnRule.mandatoryNotNull("ycode"))
                .columnRule(ColumnRule.mandatoryNotNull("code"))
                .columnRule(ColumnRule.mandatoryNotNull("postcode"))
                .columnRule(ColumnRule.mandatoryNotNull("admsite_code"))
                .columnRule(ColumnRule.mandatoryNotNull("ref_admsite_code"))
                .columnRule(ColumnRule.mandatoryNotNull("admou_code"))
                .build();
    }

    /** {@code finchqdet} — Cheque Book Detail. Key {@code [chqno, glcode]}, NOT {@code vchcode}:
     *  {@code finchqdet.vchcode} is only populated once a cheque is actually issued (null/blank on
     *  a fresh {@code 'N'} row — see {@code FinChqDet.java}'s own Javadoc), so it can't identify
     *  the row in the BEFORE snapshot. {@code (chqno, glcode)} is stable throughout — the same
     *  compound key {@code ChequeBookValidator}/{@code FinChqDetDao.findByChqnoAndGlcode} already
     *  use — captured via {@link TableRule#getFkColumns()}'s composite-key path since
     *  {@link SnapshotService} only supports a single {@code fkColumn} otherwise. Participates
     *  only when {@link DocumentContext#isChequeBookApplicable()} — a KNOWN/registered cheque
     *  number was used; {@code false} for AR (never touches the company's own issued-cheque
     *  register) and for the common synthetic/unregistered-cheque test case. Common to every
     *  domain, not GV-specific — mirrors {@link #fincosttag()}'s own conditional-participation
     *  shape. */
    private static TableRule finchqdet() {
        return TableRule.builder()
                .tableName("finchqdet")
                .businessKeyColumn("chqno")
                .businessKeyColumn("glcode")
                .fkColumns(List.of("chqno", "glcode"))
                .fkValuesResolver(ctx -> java.util.Arrays.asList(ctx.getKnownChqno(), ctx.getKnownChequeGlcode()))
                .participationCondition(DocumentContext::isChequeBookApplicable)
                .rowMatchStrategy(RowMatchStrategy.BY_KEY)
                .defaultRule(ChangeExpectation.MUST_NOT_CHANGE)
                // Item G — mandatory-not-null columns (confirmed real fields on FinChqDet.java).
                .columnRule(ColumnRule.mandatoryNotNull("bookcode"))
                .columnRule(ColumnRule.mandatoryNotNull("chqno"))
                .build();
    }

    /** {@code finchqmain} — Cheque Book Master. Key {@code [bookcode]} — unlike
     *  {@code finchqdet}, this can keep the standard single-column {@code fkColumn} mechanism
     *  unchanged: {@code bookcode} is carried as an already-resolved value on
     *  {@link DocumentContext#getKnownChequeBookcode()} (found once, during the same
     *  {@code finchqdet} lookup that determines {@link #finchqdet()}'s own participation), so no
     *  second composite lookup is needed here. Same participation condition as
     *  {@link #finchqdet()} — the two always participate together. */
    private static TableRule finchqmain() {
        return TableRule.builder()
                .tableName("finchqmain")
                .businessKeyColumn("bookcode")
                .fkColumn("bookcode")
                .fkValueResolver(ctx -> ctx.getKnownChequeBookcode())
                .participationCondition(DocumentContext::isChequeBookApplicable)
                .rowMatchStrategy(RowMatchStrategy.BY_KEY)
                .defaultRule(ChangeExpectation.MUST_NOT_CHANGE)
                .build();
    }

    /** One {@code aud2_<table>} audit-shadow table — participates only when {@code baseTable} is
     *  currently audit-active (per {@link Aud2AuditStateResolver}) <b>and</b> its paired data table
     *  itself participates for this document (via {@code dataTableRule.participates(ctx)}). The
     *  latter half matters for conditionally-participating tables like {@code fincosttag}
     *  ({@link #fincosttag()} only applies when {@link DocumentContext#isCostTagApplicable()}) —
     *  without it, {@code aud2_fincosttag} would run in {@code DiffEngine}'s Pass 2 even when
     *  {@code fincosttag} itself never entered Pass 1, hitting the "no paired data table rule"
     *  fallback and wrongly demanding a new audit row for a table that legitimately never changed
     *  (confirmed live: a real GV edit with no cost-tag-applicable line, table unchanged at
     *  rowsBefore=2/rowsAfter=2, incorrectly failed before this fix). No-op for the other pairs
     *  above, whose data tables always participate. Row-count-only INSERT check, never
     *  column-diffed (see {@code TableRule#isAuditTable()}). */
    private static TableRule auditTable(String auditTableName, TableRule dataTableRule) {
        String baseTable = dataTableRule.getTableName();
        return TableRule.builder()
                .tableName(auditTableName)
                .participationCondition(ctx -> ctx.getAuditActiveTables().contains(baseTable)
                        && dataTableRule.participates(ctx))
                .defaultRule(ChangeExpectation.INSERT)
                .build();
    }
}
