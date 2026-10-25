package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.database.DatabaseManager;
import com.gsl.database.DbAvailability;
import com.gsl.enums.GlAccountType;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.Log;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherFixtureFactory;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherCombinationFilter;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherCombinations;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherFixtureOptions;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherScenarioType;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherTag;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code testdata.source=API} source for {@code revertGeneralVoucher}: instead of claiming an existing Posted voucher from the
 * database, it creates and releases one voucher per surviving combination through the Finance API (a Type1 line of the
 * combination's account type, paid in cash - what the DB source's API backfill produces on a shortfall), so every row owns a fresh,
 * unreserved, BRS-free, current-period Posted voucher.
 *
 * <p>The closed-period negative row cannot be manufactured through the API (no month-close call), so it keeps the DB claim when
 * {@code db.available=yes} and is omitted (with a log line) otherwise.
 */
public class ApiGeneralVoucherRevertDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    private final String xmlGroupParam;
    private final String xmlTagParam;

    public ApiGeneralVoucherRevertDataSource(String xmlGroupParam, String xmlTagParam) {
        this.xmlGroupParam = xmlGroupParam;
        this.xmlTagParam = xmlTagParam;
    }

    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {
        GeneralVoucherExecutionGroup activeGroup = GeneralVoucherCombinationFilter.resolveActiveGroup(xmlGroupParam);
        Set<GeneralVoucherTag> activeTags = GeneralVoucherCombinationFilter.resolveActiveTags(xmlTagParam);

        List<GeneralVoucherVariant> variants = new ArrayList<>();
        for (GeneralVoucherVariant variant
                : GeneralVoucherFixtureFactory.assignKnownChequeFlags(GeneralVoucherCombinations.allCombinations())) {
            if (!GeneralVoucherCombinationFilter.survives(variant, activeGroup, activeTags)) continue;
            if (variant.isMultiRowPayment()) continue;   // Create-only dimension, same as the DB source
            variants.add(variant);
        }

        List<MergedFinanceGeneralVoucherData> results = ApiSessionFactory.withWebApiSession(session -> {
            long empCode = Long.parseLong(session.getUserId());
            List<MergedFinanceGeneralVoucherData> rows = new ArrayList<>();
            for (GeneralVoucherVariant variant : variants) {
                rows.add(createRow(session.getGeneralVoucherApiClient(), empCode, variant));
            }
            return rows;
        });

        if (DbAvailability.isAvailable()) {
            DbGeneralVoucherRevertDataSource.resolveClosedPeriodNegativeCandidate(DatabaseManager.getJdbi(), new HashSet<>())
                    .ifPresent(results::add);
        } else {
            Log.warn("[API] GeneralVoucherRevert: closed-period negative row omitted - db.available=no and no API can create a "
                    + "voucher in a closed period.");
        }

        int total = results.size();
        for (int i = 0; i < total; i++) {
            results.get(i).setInvocationIndex(i + 1);
            results.get(i).setTotalInvocations(total);
        }
        Log.info("[API] GeneralVoucherRevert emitted {} row(s) for '{}' - group filter={}, tag filter={}",
                total, methodName, activeGroup, activeTags.isEmpty() ? "ALL" : activeTags);
        return results;
    }

    private static MergedFinanceGeneralVoucherData createRow(GeneralVoucherApiClient api, long empCode,
                                                             GeneralVoucherVariant variant) {
        GlAccountType accountType = variant.getAccountType();
        GeneralVoucherCreateResponse created = api.createGeneralVoucherWithDefaults(accountType, 0, 0, empCode);
        List<String> log = new ArrayList<>();
        log.add("═══════════════════════════════════════════════════════════");
        log.add("  API DATA GATHERING - POSTED VOUCHER CREATED (GENERAL VOUCHER REVERT)");
        log.add("═══════════════════════════════════════════════════════════");
        log.add("  Combination='" + variant.getVariantId() + "', account type=" + accountType);
        log.add("  Created and released voucher scheme_docno='" + created.getDocScheme() + "' (vchcode=" + created.vchcode() + ")");
        log.add("═══════════════════════════════════════════════════════════");

        GeneralVoucherFixtureOptions opts = GeneralVoucherFixtureOptions.of(
                variant.getAccountType(), variant.getGlLineType(), variant.isDebitSide(), variant.isReversal(),
                variant.getPaymentVariant(), variant.getGlCount(), variant.getSiteCount());
        return MergedFinanceGeneralVoucherData.builder()
                .scenarioType(GeneralVoucherScenarioType.POSITIVE_COMBINATION)
                .scenarioTypeLabel(variant.getVariantId())
                .fixtureOptions(opts)
                .tags(GeneralVoucherCombinationFilter.tagsFor(variant))
                .existingVoucherNo(created.getDocScheme())
                .releaseStatus("P")
                .reservedDocumentKey(String.valueOf(created.vchcode()))
                .operationMode(GeneralVoucherOperationMode.REVERT)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .dbGatheringLog(log)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }
}
