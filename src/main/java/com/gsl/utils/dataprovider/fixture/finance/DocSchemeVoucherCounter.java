package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.adm.AdmDocSchemeDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import org.jdbi.v3.core.Jdbi;

/**
 * Counts the vouchers saved under a document scheme — a before/after pair around a rejected Save proves the
 * rejection created nothing ("Save on a full scheme must not create a voucher").
 */
public final class DocSchemeVoucherCounter {

    private DocSchemeVoucherCounter() {}

    /** @throws IllegalStateException if {@code docSchemeName} is not an {@code admdocscheme.docname} */
    public static long count(String docSchemeName) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        Long doccode = jdbi.onDemand(AdmDocSchemeDao.class).findDoccodeByDocname(docSchemeName);
        if (doccode == null) {
            throw new IllegalStateException("No admdocscheme row named '" + docSchemeName + "' — cannot count its vouchers.");
        }
        return jdbi.onDemand(FinVchMainDao.class).countByDoccode(doccode);
    }
}
