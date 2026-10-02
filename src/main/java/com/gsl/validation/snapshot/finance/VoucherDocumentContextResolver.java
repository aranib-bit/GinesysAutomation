package com.gsl.validation.snapshot.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinChqDetDao;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.driver.DriverManager;
import com.gsl.enums.GlAccountType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.enums.VoucherDomain;
import com.gsl.model.finance.FinChqDet;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.UserCredential;
import com.gsl.validation.snapshot.DocumentContext;
import org.jdbi.v3.core.Jdbi;

import java.util.List;

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

    /**
     * Resolves the full {@link DocumentContext} for the existing voucher identified by
     * {@code voucherNo} — see class Javadoc for what each resolved field covers.
     *
     * @param voucherNo {@code finvchmain.scheme_docno} of the document to resolve
     * @param domain    which voucher domain (GV/AR/AP) this document belongs to
     * @throws IllegalStateException if no {@code finvchmain} row matches {@code voucherNo}
     */
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

        // Round 23: cheque-book (finchqdet/finchqmain) applicability — domain-agnostic, reusing
        // the SAME lookup ChequeBookValidator's Level 5 already uses, so this naturally evaluates
        // false for AR (which never touches the company's own issued-cheque register at all) and
        // for a GV/AP document using a synthetic/unregistered cheque number (the common test-data
        // case) — no per-domain branch needed.
        FinChqDetDao finChqDetDao = jdbi.onDemand(FinChqDetDao.class);
        FinVchDet chequeDetailRow = detailRows.stream()
                .filter(d -> PaymentSelectionType.CHEQUE.dbCode().equalsIgnoreCase(d.getPaymentMode())
                        && d.getChqno() != null && !d.getChqno().isBlank())
                .findFirst()
                .orElse(null);
        FinChqDet knownChequeRow = chequeDetailRow != null
                ? finChqDetDao.findByChqnoAndGlcode(chequeDetailRow.getChqno(), chequeDetailRow.getGlcode())
                : null;
        boolean chequeBookApplicable = knownChequeRow != null;

        com.gsl.validation.snapshot.VoucherPersistenceMap persistenceMap = VoucherPersistenceMapFactory.build(domain);
        // Item U: shared with AuditStateSummary.resolveFor, which also lets Create-mode check this
        // same state BEFORE a document exists (no vchcode needed) — this method's own resolution
        // is unchanged, just delegated instead of duplicated.
        AuditStateSummary auditState = AuditStateSummary.resolveFor(persistenceMap);

        UserCredential threadUser = DriverManager.getCurrentUser();
        String currentUsername = threadUser != null ? threadUser.username() : ConfigFactory.getConfig().username();

        return DocumentContext.builder()
                .voucherNo(voucherNo)
                .vchcode(vchcode)
                .documentType(domain.getVoucherType())
                .currentUser(currentUsername)
                .persistenceMap(persistenceMap)
                .auditActiveTables(auditState.auditActiveTables())
                .auditTriggerDisabledTables(auditState.auditTriggerDisabledTables())
                .costTagApplicable(costTagApplicable)
                .chequeBookApplicable(chequeBookApplicable)
                .knownChqno(chequeDetailRow != null ? chequeDetailRow.getChqno() : null)
                .knownChequeGlcode(chequeDetailRow != null ? chequeDetailRow.getGlcode() : null)
                .knownChequeBookcode(knownChequeRow != null ? knownChequeRow.getBookcode() : null)
                .build();
    }
}
