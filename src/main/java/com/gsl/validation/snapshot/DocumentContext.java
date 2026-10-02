package com.gsl.validation.snapshot;

import com.gsl.enums.VoucherType;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Set;

/**
 * Identifies one document (voucher) for the snapshot/diff engine — doc §11, plan §1, adapted:
 * {@link #getAuditActiveTables()} is a <em>set of currently audit-active base table names</em>
 * (per {@code aud2_active_table}), not one document-level boolean — audit activation in this
 * schema is per-table, not per-document-type (see the plan's Context section).
 */
@Getter
@Builder
public class DocumentContext {

    private final String voucherNo;
    private final long vchcode;
    private final VoucherType documentType;
    private final String currentUser;
    private final VoucherPersistenceMap persistenceMap;

    /** Base table names (e.g. {@code "finvchmain"}, {@code "finvchdet"}) currently audit-active
     *  per {@code Aud2AuditStateResolver} — empty when audit tracking is off for all of them, or
     *  when the resolver spike itself couldn't run (schema mismatch — degrades gracefully). */
    @Builder.Default
    private final Set<String> auditActiveTables = Collections.emptySet();

    /** Base table names that ARE registered active (present in {@link #auditActiveTables}) but
     *  whose {@code t_<table>_aud2} trigger is physically {@code DISABLED} on this environment —
     *  a real, live-confirmed distinction from "not registered at all": {@code aud2_module}/
     *  {@code aud2_active_table} say a module should be audited, but the trigger itself can be
     *  off (maintenance, migration, never re-enabled) on any given staging instance. Resolved by
     *  {@code AuditTriggerStateResolver}. Field-level audit checks are skipped (with a visible
     *  report note, not a silent pass) for any table in this set — see {@code DiffEngine}. */
    @Builder.Default
    private final Set<String> auditTriggerDisabledTables = Collections.emptySet();

    /** {@code true} when at least one live {@code finvchdet} row for this document has
     *  {@code gl_cc_appl='Y'} (a Type2/Type3 Site-applicable line) — drives {@code fincosttag}'s
     *  {@link TableRule} participation condition. Resolved live from the DB by
     *  {@code DocumentContextResolver}, independent of any test-data assumption. */
    private final boolean costTagApplicable;

    /** {@code true} when this document's payment info uses a KNOWN/registered cheque — i.e. a
     *  {@code finvchdet} row with Cheque payment mode whose {@code (chqno, glcode)} resolves to
     *  an existing {@code finchqdet} row. Drives {@code finchqdet}/{@code finchqmain}'s
     *  {@link TableRule} participation condition — {@code false} for every AR document (AR never
     *  touches the company's own issued-cheque register) and for a GV/AP document using a
     *  synthetic/unregistered cheque number (the common test-data case). Resolved live from the
     *  DB, via the same lookup {@code ChequeBookValidator} uses. */
    @Builder.Default
    private final boolean chequeBookApplicable = false;

    /** The resolved known {@code chqno}, when {@link #chequeBookApplicable}; {@code null} otherwise. */
    private final String knownChqno;

    /** The resolved known cheque's {@code glcode}, when {@link #chequeBookApplicable}; {@code null} otherwise. */
    private final Long knownChequeGlcode;

    /** The resolved known cheque's {@code finchqdet.bookcode}, when {@link #chequeBookApplicable};
     *  {@code null} otherwise — lets {@code finchqmain}'s {@link TableRule} use the existing
     *  single-column {@code fkColumn}/{@code fkValueResolver} mechanism unchanged, since this
     *  value is already resolved by the time it's needed. */
    private final Long knownChequeBookcode;
}
