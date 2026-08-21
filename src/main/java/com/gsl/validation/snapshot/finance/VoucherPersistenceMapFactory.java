package com.gsl.validation.snapshot.finance;

import com.gsl.enums.VoucherDomain;
import com.gsl.validation.snapshot.ChangeExpectation;
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

    public static VoucherPersistenceMap build(VoucherDomain domain) {
        TableRule finvchmain = finvchmain();
        TableRule finvchdet = finvchdet();
        TableRule finpost = finpost();
        TableRule fincosttag = fincosttag();

        List<TableRule> base = List.of(
                finvchmain,
                finvchdet,
                finpost,
                fincosttag,
                auditTable("aud2_finvchmain", finvchmain),
                auditTable("aud2_finvchdet", finvchdet),
                auditTable("aud2_finpost", finpost),
                auditTable("aud2_fincosttag", fincosttag)
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
