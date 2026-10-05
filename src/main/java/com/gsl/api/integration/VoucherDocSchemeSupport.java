package com.gsl.api.integration;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.HrEmpOuDao;
import com.gsl.reports.ReportLogger;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeMode;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeResolver;
import com.gsl.utils.dataprovider.source.finance.common.DocSchemeSelection;
import org.jdbi.v3.core.Jdbi;

import java.util.List;

/**
 * DB side of choosing a doc scheme for an API-created voucher. The API's scheme list carries no
 * counter information, so the scheme is chosen here from the DB ({@link DocSchemeResolver}, AUTO
 * mode: never an exhausted, manual or dedicated-test scheme) and its NAME is handed to the API
 * client, which picks the matching entry from {@code GET docnumberschemes}.
 */
final class VoucherDocSchemeSupport {

    private VoucherDocSchemeSupport() {}

    /**
     * @param menuShortcode e.g. {@code VoucherDomain.AR.getMenuShortcode()}
     * @param ownerSiteCode the voucher's owner site ({@code admsite.code}) — also decides the OU/year
     * @param empCode       the creating employee — its {@code hrdemp_ou} sites bound the choice
     * @return the chosen {@code admdocscheme.docname}
     * @throws IllegalStateException if no scheme can still generate a number
     */
    static String resolveSchemeName(String menuShortcode, long ownerSiteCode, long empCode) {
        Jdbi jdbi = DatabaseManager.getJdbi();
        List<Long> siteCodes = jdbi.onDemand(HrEmpOuDao.class).findSiteCodesByEcode(empCode);
        DocSchemeSelection selection = DocSchemeResolver.resolve(
                jdbi, menuShortcode, siteCodes, ownerSiteCode, DocSchemeMode.AUTO);
        ReportLogger.dataBackfill("[DocScheme] {} → '{}' (doccode={}, remaining={})", menuShortcode,
                selection.docName(), selection.docCode(), selection.getChosen().getRemaining());
        return selection.docName();
    }
}
