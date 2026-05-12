package com.gsl.utils.dataprovider.source.finance;

import com.gsl.config.ConfigFactory;
import com.gsl.database.DatabaseManager;
import com.gsl.database.MenuOperationKey;
import com.gsl.database.DbIdQueue;
import com.gsl.database.dao.finance.FinGlDao;
import com.gsl.database.dao.finance.FinSlDao;
import com.gsl.database.dao.finance.FinVchDetDao;
import com.gsl.database.dao.finance.FinVchMainDao;
import com.gsl.model.finance.FinGl;
import com.gsl.model.finance.FinSl;
import com.gsl.model.finance.FinVchDet;
import com.gsl.model.finance.FinVchMain;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SubLedgerInformation;
import com.gsl.testdata.MasterTestData;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.jdbi.v3.core.Jdbi;
import org.testng.SkipException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads AR Voucher test data for <b>Edit</b> operations by querying an existing
 * voucher from {@code finvchmain} + {@code finvchdet}.
 *
 * <h3>Data flow</h3>
 * <ol>
 *   <li>Polls one {@code vchcode} from {@link DbIdQueue} key
 *       {@code "FinanceARVoucher.Edit"} — guaranteed unique per thread.</li>
 *   <li>Fetches the voucher header ({@code finvchmain}) and detail rows
 *       ({@code finvchdet}).</li>
 *   <li>Resolves site name, GL name, SL names via existing DAOs.</li>
 *   <li>Builds {@link MergedFinanceArVoucherData} with:
 *       <ul>
 *         <li>{@code existingVoucherNo} — the {@code scheme_docno} used to locate
 *             the voucher in the UI list page.</li>
 *         <li>{@code documentInformation} — existing site/GL with a <em>new</em>
 *             document date, reference, and remarks.</li>
 *         <li>{@code subLedgerInformationList} — existing SL rows (amounts unchanged;
 *             the Edit test verifies they are preserved after save).</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Scope</h3>
 * Targets vouchers with {@code release_status IN ('N','U','P')}.
 * For posted vouchers ({@code 'P'}) the test class handles unpost + fintag check
 * before applying edits.
 */
public class DbArVoucherEditDataSource implements TestDataSource<MergedFinanceArVoucherData> {

    @Override
    public List<MergedFinanceArVoucherData> load(String testMethodName) {
        Long vchcode = DbIdQueue.getNextId(MenuOperationKey.build("FinanceARVoucher", "Modify"));
        if (vchcode == null) {
            throw new SkipException(
                    "[DbArVoucherEditDataSource] No AR Voucher available for Edit. " +
                    "Ensure finvchmain has rows WHERE vchtype='R' AND release_status IN ('N','U','P').");
        }

        Log.info("[DB-Edit] Fetching AR Voucher for Edit: vchcode={}", vchcode);

        Jdbi jdbi = DatabaseManager.getJdbi();

        // 1. Header
        FinVchMain header = jdbi.onDemand(FinVchMainDao.class)
                .findByVoucherCode(vchcode)
                .orElseThrow(() -> new IllegalStateException(
                        "[DbArVoucherEditDataSource] finvchmain row not found for vchcode=" + vchcode));

        // 2. Site name (admsite.name WHERE code = siteid)
        String siteName = jdbi.withHandle(h ->
                h.createQuery("SELECT name FROM admsite WHERE code = :code")
                 .bind("code", header.getSiteid())
                 .mapTo(String.class)
                 .findFirst()
                 .orElseThrow(() -> new IllegalStateException(
                         "[DbArVoucherEditDataSource] admsite not found for siteid=" + header.getSiteid()))
        );

        // 3. GL name
        FinGl gl = jdbi.onDemand(FinGlDao.class).findByGlCode(header.getGlcode());
        if (gl == null) throw new IllegalStateException(
                "[DbArVoucherEditDataSource] fingl not found for glcode=" + header.getGlcode());

        // 4. Detail rows
        List<FinVchDet> details = jdbi.onDemand(FinVchDetDao.class).findByVoucherCode(vchcode);
        if (details.isEmpty()) {
            Log.warn("[DB-Edit] No finvchdet rows found for vchcode={} — SL list will be empty.", vchcode);
        }

        // 5. Resolve SL info for each detail row
        List<SubLedgerInformation> slRows = resolveSlRows(jdbi, details);

        // 6. Build DocumentInformation with NEW date / reference / remarks (same site + GL)
        DocumentInformation docInfo = new DocumentInformation(
                header.getSchemeDocno(),          // testCaseId — reuse scheme_docno for traceability
                siteName,                          // ownersite
                header.getSchemeDocno(),           // voucherno — existing doc number
                gl.getGlname(),                    // cashbank
                FakerUtils.randomRemarks(),        // remarks — NEW value
                LocalDate.now(),                   // documentdate — NEW value (today)
                FakerUtils.randomReference()       // reference — NEW value
        );

        // 7. MasterTestData — login credentials from config
        MasterTestData master = new MasterTestData(
                testMethodName, null, null, null,
                "FIN-AR-EDIT-" + vchcode, 1);

        MergedFinanceArVoucherData editData = MergedFinanceArVoucherData.builder()
                .existingVoucherNo(header.getSchemeDocno())
                .releaseStatus(header.getReleaseStatus())
                .masterTestData(master)
                .documentInformation(docInfo)
                .subLedgerInformationList(slRows)
                .selectDocumentList(List.of())
                .referenceSitewiseFIFOList(List.of())
                .paymentInformationList(List.of())
                .paymentProvided(false)
                .needsIgnoreProceed(false)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();

        Log.info("[DB-Edit] Built edit data for vchcode={}, scheme_docno='{}', new date={}, {} SL row(s)",
                vchcode, header.getSchemeDocno(), docInfo.getDocumentdate(), slRows.size());

        return List.of(editData);
    }

    /**
     * Resolves each {@code finvchdet} row into a {@link SubLedgerInformation} by
     * looking up the SL name + SL ID from {@code finsl} and the GL name from {@code fingl}.
     */
    private static List<SubLedgerInformation> resolveSlRows(Jdbi jdbi, List<FinVchDet> details) {
        FinSlDao slDao = jdbi.onDemand(FinSlDao.class);
        FinGlDao glDao = jdbi.onDemand(FinGlDao.class);
        List<SubLedgerInformation> rows = new ArrayList<>();

        for (int i = 0; i < details.size(); i++) {
            FinVchDet det = details.get(i);

            FinSl sl = slDao.findBySlcode(det.getSlcode());
            FinGl gl = glDao.findByGlCode(det.getGlcode());

            String slName = (sl != null) ? sl.getSlname() : "SL-" + det.getSlcode();
            String slId   = (sl != null) ? sl.getSlid()   : null;
            String glName = (gl != null) ? gl.getGlname() : "GL-" + det.getGlcode();

            // Use camount for credit-side rows (AR voucher credits the SL)
            rows.add(new SubLedgerInformation(
                    /* testCaseID      */ null,
                    /* lineNo          */ String.valueOf(i + 1),
                    /* subLedger       */ slName,
                    /* id              */ slId,
                    /* generalLedger   */ glName,
                    /* referenceSite   */ null,
                    /* ledgerNarration */ det.getNarration(),
                    /* isReversal      */ "N",
                    /* amountCr        */ det.getCamount(),
                    /* adjustmentType  */ null,
                    /* paymentSelection*/ null
            ));
        }
        return rows;
    }
}
