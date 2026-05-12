package com.gsl.validation.db.finance;

import com.gsl.database.DatabaseManager;
import com.gsl.database.dao.finance.FinPostDao;
import com.gsl.database.dao.finance.FinTagDao;
import com.gsl.model.finance.FinPost;
import com.gsl.model.finance.FinTag;
import com.gsl.model.finance.FinVchMain;
import com.gsl.reports.ReportLogger;
import com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher.SelectDocument;
import com.gsl.testdata.dto.MergedFinanceArVoucherData;
import com.gsl.utils.dataprovider.fixture.finance.SelectDocumentVariant;
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Level 4a — fintag Adjustment Tagging Validation.
 *
 * SHARED TABLE NOTE: fintag is used by Finance and Procurement.
 * Queries scoped to postcode1 values from the current voucher's finpost entries only.
 *
 * NON-BLOCKING: all failures collected via SoftAssertions (AssertJ).
 * ADVANCE_PAYMENT: skipped (no SelectDocument → 0 fintag rows expected).
 */
public class FinTagValidator {

    private final FinTagDao  finTagDao;
    private final FinPostDao finPostDao;

    public FinTagValidator() {
        this.finTagDao  = DatabaseManager.getJdbi().onDemand(FinTagDao.class);
        this.finPostDao = DatabaseManager.getJdbi().onDemand(FinPostDao.class);
    }

    public void validate(VoucherDbContext context,
                         FinVchMain header,
                         List<FinPost> postEntries) {

        String voucherNo = context.getVoucherNo();
        MergedFinanceArVoucherData data = context.getTestData();
        List<SelectDocument> selectDocs = data.getSelectDocumentList();

        if (selectDocs == null || selectDocs.isEmpty()) {
            ReportLogger.info("Level 4a — skipped [" + voucherNo + "]: ADVANCE_PAYMENT (no SelectDocument)");
            return;
        }

        ReportLogger.info("Level 4a — fintag validation for voucherNo [" + voucherNo + "]");

        long vchcode = header.getVchcode();
        List<FinTag> tags = finTagDao.findByEntCode1(vchcode);

        if (tags.isEmpty()) {
            ReportLogger.info("Level 4a — no fintag rows found for entcode1 (vchcode) [" + vchcode + "]");
            return;
        }

        BigDecimal totalTagged = finTagDao.sumTaggedAmountByEntCode1(vchcode);
        // Grid:GREATER — tagged amount equals adj entry total, not grid amount (finvchmain.damount)
        final boolean isGridGreater = data.getGridRelation() == SelectDocumentVariant.GridRelation.GREATER;

        SoftAssertions.assertSoftly(soft -> {
            // Cardinality: total tags across voucher == number of SelectDocuments
            soft.assertThat(tags.size())
                    .as("[Level 4a] fintag count for entcode1 [" + vchcode
                            + "]: expected " + selectDocs.size() + " found " + tags.size())
                    .isEqualTo(selectDocs.size());

            if (isGridGreater) {
                BigDecimal expectedTagTotal = selectDocs.stream()
                        .map(SelectDocument::getAdjustmentAmount)
                        .filter(a -> a != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                soft.assertThat(totalTagged)
                        .as("[Level 4a] Grid:GREATER — SUM(fintag.amount)=" + totalTagged
                                + " != SUM(selectDoc.adjustmentAmount)=" + expectedTagTotal
                                + " for entcode1 [" + vchcode + "]")
                        .isEqualByComparingTo(expectedTagTotal);
            } else {
                soft.assertThat(totalTagged)
                        .as("[Level 4a] SUM(fintag.amount)=" + totalTagged
                                + " != finvchmain.damount=" + header.getDamount()
                                + " for entcode1 [" + vchcode + "]")
                        .isEqualByComparingTo(header.getDamount());
            }

            for (FinTag tag : tags)
                validateTagRow(soft, tag, selectDocs, voucherNo);
        });

        ReportLogger.info("<b>fintag rows:</b>");
        ReportLogger.infoTable(buildTagTable(tags));
        ReportLogger.info("Level 4a — PASSED for voucherNo [" + voucherNo + "]");
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

    private void validateTagRow(SoftAssertions soft, FinTag tag,
                                 List<SelectDocument> selectDocs, String voucherNo) {

        // FK: postcode2 must resolve to a real finpost row
        Optional<FinPost> invPost = finPostDao.findByPostCode(tag.getPostcode2());
        soft.assertThat(invPost)
                .as("[Level 4a] fintag.postcode2 [" + tag.getPostcode2()
                        + "] has no finpost row — broken FK for [" + voucherNo + "]")
                .isPresent();

        // Tagged amount matches SelectDocument.adjustmentAmount
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
}
