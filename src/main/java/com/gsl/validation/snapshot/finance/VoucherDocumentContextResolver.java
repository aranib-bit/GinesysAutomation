package com.gsl.validation.snapshot.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.GlAccountType;
import com.gsl.enums.VoucherDomain;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.UserCredential;
import com.gsl.validation.snapshot.DocumentContext;
import com.gsl.validation.snapshot.audit.AuditTriggerStateResolver;
import org.jdbi.v3.core.Jdbi;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a {@link DocumentContext} for one existing voucher, by its {@code scheme_docno} —
 * plan §2. Renamed from the GV-only {@code DocumentContextResolver} it replaces: fetches the
 * header via the existing {@code FinVchMainDao.findBySchemeDocno} (same {@code finvchmain}
 * table regardless of domain), resolves whether {@code fincosttag} should participate (live,
 * not inferred from test data — common to every domain, see {@link VoucherPersistenceMapFactory}),
 * resolves current audit-active state, and builds the domain's
 * {@link com.gsl.validation.snapshot.VoucherPersistenceMap} via
 * {@link VoucherPersistenceMapFactory#build(VoucherDomain)}.
 *
 * <p>{@code fincosttag} participation requires <b>both</b> {@code finvchdet.gl_cc_appl='Y'}
 * (Site Applicable) <b>and</b> the line's {@code fingl} row being an Income or Expense ledger
 * ({@code fingl.type} — see {@link FinGl#accountType()}/{@link GlAccountType}) — a Site-applicable
 * Asset/Liability line does not recreate a {@code fincosttag} row.
 */
public class VoucherDocumentContextResolver {

    private final Aud2AuditStateResolver auditStateResolver = new Aud2AuditStateResolver();
    private final AuditTriggerStateResolver triggerStateResolver = new AuditTriggerStateResolver();

    public DocumentContext resolve(String voucherNo, VoucherDomain domain) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        FinVchMain header = jdbi.onDemand(FinVchMainDao.class).findBySchemeDocno(voucherNo)
                .orElseThrow(() -> new IllegalStateException(
                        "[VoucherDocumentContextResolver] finvchmain row not found for scheme_docno='" + voucherNo + "'"));

        long vchcode = header.getVchcode();
        List<FinVchDet> detailRows = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(vchcode);
        FinGlDao finGlDao = jdbi.onDemand(FinGlDao.class);
        boolean costTagApplicable = detailRows.stream().anyMatch(d -> {
            if (!"Y".equalsIgnoreCase(d.getGlCcAppl())) return false;
            FinGl gl = finGlDao.findByGlCode(d.getGlcode());
            if (gl == null) return false;
            GlAccountType accountType = gl.accountType();
            return accountType == GlAccountType.INCOME || accountType == GlAccountType.EXPENSE;
        });

        com.gsl.validation.snapshot.VoucherPersistenceMap persistenceMap = VoucherPersistenceMapFactory.build(domain);
        Set<String> tableNames = persistenceMap.getTableRules().stream()
                .filter(rule -> !rule.isAuditTable())
                .map(rule -> rule.getTableName())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> auditActiveTables = auditStateResolver.resolveAuditActiveTables(tableNames);

        // Trigger-drift check (user's explicit caution): aud2_active_table registration alone
        // doesn't guarantee the trigger is physically enabled on this environment — checked
        // per-table, only for tables that ARE registered active (no point checking a table whose
        // audit isn't even configured).
        Set<String> auditTriggerDisabledTables = new HashSet<>();
        for (String tableName : auditActiveTables) {
            AuditTriggerStateResolver.TriggerStatus status = triggerStateResolver.resolveTriggerStatus(tableName);
            if (status == AuditTriggerStateResolver.TriggerStatus.DISABLED) {
                auditTriggerDisabledTables.add(tableName);
            }
        }

        UserCredential threadUser = DriverManager.getCurrentUser();
        String currentUsername = threadUser != null ? threadUser.username() : ConfigFactory.getConfig().username();

        return DocumentContext.builder()
                .voucherNo(voucherNo)
                .vchcode(vchcode)
                .documentType(domain.getVoucherType())
                .currentUser(currentUsername)
                .persistenceMap(persistenceMap)
                .auditActiveTables(auditActiveTables)
                .auditTriggerDisabledTables(auditTriggerDisabledTables)
                .costTagApplicable(costTagApplicable)
                .build();
    }
}
