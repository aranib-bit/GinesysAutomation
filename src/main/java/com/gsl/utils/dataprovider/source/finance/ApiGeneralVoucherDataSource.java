package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.dataprovider.fixture.LiveGvDbRefs;
import com.gsl.utils.dataprovider.fixture.finance.ApiGvRefsResolver;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherPaymentVariant;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTemplateLoader;
import com.gsl.utils.dataprovider.source.TestDataSource;
import org.testng.SkipException;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code testdata.source=API} source for the General Voucher Create matrix and the MIXED-types voucher.
 *
 * <p>Same two-phase shape as {@link DbGeneralVoucherDataSource}: {@link #load} emits the very same placeholders (the combination
 * matrix, execution-group/tag filter and cheque/payment weighting are pure in-memory logic and are reused as-is), and
 * {@link #loadFreshForScenario}/{@link #loadFreshForMixedTypes} resolve one placeholder at test time — here through the Finance
 * API ({@link ApiGvRefsResolver}) instead of the database. The API session is a pooled web user other than the UI user (see
 * {@link ApiSessionFactory#withWebApiSession}).
 */
public class ApiGeneralVoucherDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    private final DbGeneralVoucherDataSource placeholderSource;

    public ApiGeneralVoucherDataSource(String xmlGroupParam, String xmlTagParam) {
        this.placeholderSource = new DbGeneralVoucherDataSource(xmlGroupParam, xmlTagParam);
    }

    /** Placeholder emission — no DB or API call happens here (the Db source's {@code load} is pure in-memory). */
    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {
        return placeholderSource.load(methodName);
    }

    /** Resolves one combination placeholder through the API. */
    public static MergedFinanceGeneralVoucherData loadFreshForScenario(MergedFinanceGeneralVoucherData placeholder, String methodName) {
        GeneralVoucherPaymentVariant paymentVariant = placeholder.getFixtureOptions().getPaymentVariant();
        if (paymentVariant.isBlockedPendingUiDiscovery()) {
            throw new SkipException("Skipped — " + paymentVariant + " is blocked pending a manual UI-discovery spike. Variant: "
                    + placeholder.getScenarioTypeLabel());
        }
        return ApiSessionFactory.withWebApiSession(session -> resolve(session, placeholder));
    }

    /** Resolves the MIXED-types voucher (Asset Type1 + Liability Type2 + Income Type3, CASH) through the API. */
    public static MergedFinanceGeneralVoucherData loadFreshForMixedTypes(MergedFinanceGeneralVoucherData placeholder, String methodName) {
        return ApiSessionFactory.withWebApiSession(session -> {
            List<MergedFinanceGeneralVoucherData> perType = new ArrayList<>();
            perType.add(resolve(session, onePlaceholder(GlAccountType.ASSET, GlLineType.TYPE1_NONE, true)));
            perType.add(resolve(session, onePlaceholder(GlAccountType.LIABILITY, GlLineType.TYPE2_SITE_ONLY, true)));
            perType.add(resolve(session, onePlaceholder(GlAccountType.INCOME, GlLineType.TYPE3_SITE_AND_SUBLEDGER, false)));
            MergedFinanceGeneralVoucherData data = GeneralVoucherTemplateLoader.mergeIntoMixedVoucher(perType);
            data.setScenarioType(placeholder.getScenarioType());
            data.setTags(placeholder.getTags());
            data.setMasterTestData(placeholder.getMasterTestData());
            return data;
        });
    }

    private static MergedFinanceGeneralVoucherData onePlaceholder(GlAccountType type, GlLineType lineType, boolean debitSide) {
        GeneralVoucherFixtureOptions opts = GeneralVoucherFixtureOptions.of(
                type, lineType, debitSide, false, GeneralVoucherPaymentVariant.CASH, 1, 1);
        return MergedFinanceGeneralVoucherData.builder()
                .scenarioType(GeneralVoucherScenarioType.POSITIVE_COMBINATION)
                .fixtureOptions(opts)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .build();
    }

    private static MergedFinanceGeneralVoucherData resolve(ApiSessionBundle session, MergedFinanceGeneralVoucherData placeholder) {
        GeneralVoucherFixtureOptions opts = placeholder.getFixtureOptions();
        List<String> log = new ArrayList<>();
        log.add("═══════════════════════════════════════════════════════════");
        log.add("  API DATA GATHERING — RESOLVED REFERENCES (GENERAL VOUCHER)");
        log.add("═══════════════════════════════════════════════════════════");

        ApiGvRefsResolver.Caller caller = new ApiGvRefsResolver.Caller(
                session.getGeneralVoucherApiClient(), Long.parseLong(session.getUserId()),
                session.getLedgerApiClient(), session.getSubLedgerOthersApiClient());
        ApiGvRefsResolver.OwnerContext owner = ApiGvRefsResolver.resolveOwner(caller, opts.cashBankType(), log);
        LiveGvDbRefs refs = ApiGvRefsResolver.resolve(caller, owner, opts,
                DbGeneralVoucherDataSource.buildVariantFromOptions(placeholder, opts), log);

        MergedFinanceGeneralVoucherData data = placeholder.getScenarioType().loadData(refs);
        data.setFixtureOptions(opts);
        data.setTags(placeholder.getTags());
        data.setMasterTestData(placeholder.getMasterTestData());
        log.add("═══════════════════════════════════════════════════════════");
        data.setDbGatheringLog(log);
        return data;
    }
}
