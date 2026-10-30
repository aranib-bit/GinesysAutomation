package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dialect.DbDialectFactory;
import com.gsl.database.query.AdmClsQuery;
import com.gsl.database.query.FinVchRefQuery;
import com.gsl.model.finance.AdmCls;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
import org.jdbi.v3.core.Jdbi;

import java.util.List;
import java.util.Optional;

/**
 * The handful of lookups {@link ArVoucherTemplateLoader#selectDocumentTemplate} needs beyond the already-resolved
 * {@code ArVoucherLiveDbRefs}: reference uniqueness, an alternative owner site, an SL's class name, the Cash/Bank GL code,
 * a known cheque leaf and a classification. The DB implementation ({@link #db}) is the behaviour the loader always had; the API
 * data source supplies its own implementation so the same template runs with {@code db.available=no}.
 */
public interface ArTemplateLookups {

    /** True when {@code reference} is already used by a voucher (so a fresh one is needed). */
    boolean referenceUsed(String reference);

    /** A different active {@code OS-OO-CM} site than {@code ownerSiteName}, if one exists. */
    Optional<String> alternativeOwnerSite(String ownerSiteName);

    /** Classification name (e.g. "Customer") of the sub-ledger with {@code slId}. */
    String slClassName(String slId);

    /** {@code fingl.glcode} of the Cash/Bank GL named {@code cashBankName}. */
    long cashBankGlcode(String cashBankName);

    /** A real, unused cheque leaf: of {@code thisVoucherGlcode} itself, or (when {@code differentBank}) of another bank. */
    ChequeInstrumentResolver.ChequeResolution knownCheque(long thisVoucherGlcode, boolean differentBank);

    /** Classification by code. */
    Optional<AdmCls> classification(long clsCode);

    /** The database-backed implementation used for {@code testdata.source=DB}. */
    static ArTemplateLookups db(Jdbi jdbi, List<Long> siteCodes) {
        return new ArTemplateLookups() {
            @Override
            public boolean referenceUsed(String reference) {
                return FinVchRefQuery.isReferenceUsed(jdbi, reference, siteCodes);
            }

            @Override
            public Optional<String> alternativeOwnerSite(String ownerSiteName) {
                String sql =
                        "SELECT a.name FROM admsite a " +
                        "WHERE a.sitetype = 'OS-OO-CM' " +
                        "  AND a.ext = 'N' " +
                        "  AND a.name <> :ownerSite " +
                        "ORDER BY a.code " +
                        DbDialectFactory.current().limit1();
                return jdbi.withHandle(handle -> handle.createQuery(sql)
                        .bind("ownerSite", ownerSiteName)
                        .mapTo(String.class)
                        .findFirst());
            }

            @Override
            public String slClassName(String slId) {
                return AdmClsQuery.findClsnameBySlId(jdbi, slId);
            }

            @Override
            public long cashBankGlcode(String cashBankName) {
                return jdbi.onDemand(FinGlDao.class).findByGlName(cashBankName).getGlcode();
            }

            @Override
            public ChequeInstrumentResolver.ChequeResolution knownCheque(long thisVoucherGlcode, boolean differentBank) {
                return differentBank
                        ? ChequeInstrumentResolver.resolveKnownDifferentBank(jdbi, thisVoucherGlcode)
                        : ChequeInstrumentResolver.resolveKnownSameBank(jdbi, thisVoucherGlcode);
            }

            @Override
            public Optional<AdmCls> classification(long clsCode) {
                return AdmClsQuery.findByClsCode(jdbi, clsCode);
            }
        };
    }
}
