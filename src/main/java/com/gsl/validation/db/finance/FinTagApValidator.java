package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.database.dao.finance.FinTagDao;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinTag;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ActualResultsHolder;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.APVoucher.SelectDocument;
import com.gsl.testdata.dto.MergedFinanceApVoucherData;
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Level 4a — fintag Adjustment Tagging Validation for AP Voucher.
 *
 * Parallel to FinTagValidator (AR). Key differences:
 *   - Accepts ApVoucherDbContext directly (no VoucherDbContext shim needed)
 *   - Uses MergedFinanceApVoucherData and AP SelectDocument
 *   - Skip check: selectDocumentList null/empty → ADVANCE_PAYMENT, no fintag expected
 *   - Amount check: SUM(fintag.amount) == finvchmain.camount (AP credit side carries total)
 */
public class FinTagApValidator {

    private final FinTagDao  finTagDao;
    private final FinPostDao finPostDao;

    public FinTagApValidator() {
        this.finTagDao  = DatabaseManager.getJdbi().onDemand(FinTagDao.class);
        this.finPostDao = DatabaseManager.getJdbi().onDemand(FinPostDao.class);
    }

    public void validate(ApVoucherDbContext context, FinVchMain header, List<FinPost> postEntries) {
        String voucherNo = context.getVoucherNo();
        MergedFinanceApVoucherData data = context.getTestData();
        List<SelectDocument> selectDocs = data.getSelectDocumentList();

        if (selectDocs == null || selectDocs.isEmpty()) {
            ReportLogger.info("Level 4a — skipped [" + voucherNo + "]: ADVANCE_PAYMENT (no SelectDocument)");
            ActualResultsHolder.get().setTagCount(-1);
            return;
        }

        ReportLogger.info("Level 4a — fintag validation for voucherNo [" + voucherNo + "]");
        com.gsl.reports.ValidationBuffer.start();

        long vchcode = header.getVchcode();
        List<FinTag> tags = finTagDao.findByEntCode1(vchcode);
        ActualResultsHolder.get().setTagCount(tags.size());

        if (tags.isEmpty()) {
            ReportLogger.info("Level 4a — no fintag rows found for entcode1 (vchcode) [" + vchcode + "]");
            return;
        }

        BigDecimal totalTagged = finTagDao.sumTaggedAmountByEntCode1(vchcode);

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(tags.size())
                    .as("[Level 4a] fintag count for entcode1 [" + vchcode
                            + "]: expected " + selectDocs.size() + " found " + tags.size())
                    .isEqualTo(selectDocs.size());

            // AP: credit side carries the payment total
            soft.assertThat(totalTagged)
                    .as("[Level 4a] SUM(fintag.amount)=" + totalTagged
                            + " != finvchmain.camount=" + header.getCamount()
                            + " for entcode1 [" + vchcode + "]")
                    .isEqualByComparingTo(header.getCamount());

            for (FinTag tag : tags)
                validateTagRow(soft, tag, selectDocs, voucherNo);
        });

        com.gsl.reports.ValidationBuffer.flushAsPreBlock();
        ReportLogger.infoLabeledTable("fintag rows", buildTagTable(tags));
        ReportLogger.infoHtml(buildLevel4aSummary(tags.size(), totalTagged, header.getCamount()));
        ReportLogger.info("Level 4a — PASSED for voucherNo [" + voucherNo + "]");
    }

    private static String buildLevel4aSummary(int tagCount, BigDecimal totalTagged, BigDecimal camount) {
        return "<div style='font-family:monospace;font-size:12px;line-height:1.8;padding:8px 12px;"
            + "background:#f0fff4;border-left:4px solid #38a169;border-radius:2px;margin:4px 0;'>"
            + "<b>Level 4a &mdash; fintag adjustment check</b><br/>"
            + "&#10003;&nbsp; Tagged entries: <b>" + tagCount + "</b><br/>"
            + "&#10003;&nbsp; SUM(fintag.amount) = <b>" + totalTagged + "</b>"
            + " matches finvchmain.camount = <b>" + camount + "</b><br/>"
            + "</div>";
    }

    private void validateTagRow(SoftAssertions soft, FinTag tag,
                                 List<SelectDocument> selectDocs, String voucherNo) {

        Optional<FinPost> invPost = finPostDao.findByPostCode(tag.getPostcode2());
        soft.assertThat(invPost)
                .as("[Level 4a] fintag.postcode2 [" + tag.getPostcode2()
                        + "] has no finpost row — broken FK for [" + voucherNo + "]")
                .isPresent();

        if (tag.getAmount() != null && invPost.isPresent()) {
            String entNo = invPost.get().getEntno();
            SelectDocument matched = selectDocs.stream()
                    .filter(d -> entNo != null && entNo.equalsIgnoreCase(d.getDocumentNumber()))
                    .findFirst().orElse(null);
            if (matched != null && matched.getAdjustmentAmount() != null)
                soft.assertThat(tag.getAmount())
                        .as("[Level 4a] fintag.amount=" + tag.getAmount()
                                + " != SelectDocument.adjustmentAmount="
                                + matched.getAdjustmentAmount()
                                + " for invoice [" + entNo + "]")
                        .isEqualByComparingTo(matched.getAdjustmentAmount());
        }

        soft.assertThat(tag.getSlcode())
                .as("[Level 4a] fintag.slcode invalid for postcode1 [" + tag.getPostcode1() + "]")
                .isGreaterThan(0L);
        soft.assertThat(tag.getAmount())
                .as("[Level 4a] fintag.amount null for postcode1 [" + tag.getPostcode1() + "]")
                .isNotNull();
        if (tag.getAmount() != null)
            soft.assertThat(tag.getAmount())
                    .as("[Level 4a] fintag.amount not positive: " + tag.getAmount())
                    .isGreaterThan(BigDecimal.ZERO);
        soft.assertThat(tag.getEnttype1())
                .as("[Level 4a] fintag.enttype1 null for postcode1 [" + tag.getPostcode1() + "]")
                .isNotNull();
        soft.assertThat(tag.getEnttype2())
                .as("[Level 4a] fintag.enttype2 null for postcode2 [" + tag.getPostcode2() + "]")
                .isNotNull();
    }

    private static String[][] buildTagTable(List<FinTag> rows) {
        String[] headers = {"postcode1","entcode1","postcode2","entcode2","slcode","amount","enttype1","enttype2"};
        String[][] table = new String[rows.size() + 1][headers.length];
        table[0] = headers;
        for (int i = 0; i < rows.size(); i++) {
            FinTag r = rows.get(i);
            table[i + 1] = new String[]{
                str(r.getPostcode1()), str(r.getEntcode1()), str(r.getPostcode2()),
                str(r.getEntcode2()),  str(r.getSlcode()),   str(r.getAmount()),
                str(r.getEnttype1()),  str(r.getEnttype2())
            };
        }
        return table;
    }

    private static String str(Object o) { return o == null ? "—" : o.toString(); }
    private static String str(long v)   { return String.valueOf(v); }
}
