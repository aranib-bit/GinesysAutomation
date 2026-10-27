package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.model.generalvoucher.CostCenterAllocation;
import com.gsl.api.model.generalvoucher.CostCenterSiteLookupResult;
import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import com.gsl.enums.PaymentSelectionType;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DetailLineEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.DocumentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.ExpenseIncomeDetail;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInformation;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.SiteAllocation;
import com.gsl.testdata.dto.GeneralVoucherOperationMode;
import com.gsl.testdata.dto.MergedFinanceGeneralVoucherData;
import com.gsl.utils.DateUtils;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.ValidDateRangeFinder;
import com.gsl.utils.cheque.ChequeInstrumentResolver;
import com.gsl.utils.dataprovider.fixture.GeneralVoucherEditFixtureFactory;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditCombinationFilter;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditCombinations;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditExecutionGroup;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.dataprovider.fixture.finance.VoucherEditDateVariant;
import com.gsl.utils.dataprovider.source.TestDataSource;
import com.gsl.utils.payment.PaymentReferenceGenerator;
import org.testng.SkipException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code testdata.source=API} source for {@code editGeneralVoucherHeader/DetailLine/PaymentInfo}. Works with {@code db.available=no}: it never
 * touches {@code DatabaseManager}. For every surviving {@link GeneralVoucherEditVariant} it creates its OWN voucher through the Finance API
 * ({@link ApiGeneralVoucherEditFixtures}), reads it back with {@code rootview}, resolves ledgers / sub-ledgers / cost-centre sites / cheques
 * through the API lookups and the new document date by probing the application's own voucher-date validation, then hands the pieces to
 * {@link GeneralVoucherEditRowAssembler} — the same final step the DB source uses.
 *
 * <p>One row per variant, never combined (Mandatory = header 4, detail 2, payment 3). A variant whose fixture or lookups cannot be produced
 * does NOT drop out: it becomes its own row carrying {@code fixtureFailure}, which the test fails on by name, so the row count stays visible.
 * The closed-period negative variant cannot be manufactured (no month-close API) and is omitted with a log line.
 */
public class ApiGeneralVoucherEditDataSource implements TestDataSource<MergedFinanceGeneralVoucherData> {

    private final String xmlEditGroupParam;

    public ApiGeneralVoucherEditDataSource(String xmlEditGroupParam) {
        this.xmlEditGroupParam = xmlEditGroupParam;
    }

    @Override
    public List<MergedFinanceGeneralVoucherData> load(String methodName) {
        if (!"editGeneralVoucherHeader".equals(methodName) && !"editGeneralVoucherDetailLine".equals(methodName)
                && !"editGeneralVoucherPaymentInfo".equals(methodName)) {
            throw new SkipException("[ApiGeneralVoucherEditDataSource] Unknown method '" + methodName
                    + "' — only editGeneralVoucherHeader/editGeneralVoucherDetailLine/editGeneralVoucherPaymentInfo are supported.");
        }
        GeneralVoucherEditExecutionGroup activeGroup = GeneralVoucherEditCombinationFilter.resolveActiveGroup(xmlEditGroupParam);

        List<GeneralVoucherEditVariant> variants = new ArrayList<>();
        for (GeneralVoucherEditVariant variant : GeneralVoucherEditCombinations.allCombinations()) {
            if (!GeneralVoucherEditCombinationFilter.survives(variant, activeGroup)) continue;
            if (!GeneralVoucherEditFixtureFactory.shouldRun(variant.getScenarioType(), methodName)) continue;
            if (variant.isRequireClosedPeriod()) {
                Log.warn("[API-Edit] Variant '{}' omitted: no API can close an accounting month, so a closed-period voucher cannot be created.",
                        variant.getVariantId());
                continue;
            }
            variants.add(variant);
        }

        List<MergedFinanceGeneralVoucherData> results = ApiSessionFactory.withWebApiSession(session -> {
            GeneralVoucherApiClient api = session.getGeneralVoucherApiClient();
            long empCode = Long.parseLong(session.getUserId());
            List<MergedFinanceGeneralVoucherData> rows = new ArrayList<>();
            for (GeneralVoucherEditVariant variant : variants) {
                rows.add(buildOrFailRow(api, empCode, variant, session.getLedgerApiClient(), session.getSubLedgerOthersApiClient()));
            }
            return rows;
        });

        int total = results.size();
        for (int i = 0; i < total; i++) {
            results.get(i).setInvocationIndex(i + 1);
            results.get(i).setTotalInvocations(total);
        }
        Log.info("[API] GeneralVoucherEdit emitted {} row(s) for '{}' — group filter={}", total, methodName, activeGroup);
        return results;
    }

    // ───────────────────────────── one row ─────────────────────────────

    private static MergedFinanceGeneralVoucherData buildOrFailRow(GeneralVoucherApiClient api, long empCode, GeneralVoucherEditVariant variant,
                                                                  com.gsl.api.client.LedgerApiClient ledgerClient,
                                                                  com.gsl.api.client.SubLedgerOthersApiClient subLedgerOthersClient) {
        List<String> log = new ArrayList<>();
        log.add("═══════════════════════════════════════════════════════════");
        log.add("  API DATA GATHERING — FRESH VOUCHER CREATED (GENERAL VOUCHER EDIT)");
        log.add("═══════════════════════════════════════════════════════════");
        log.add("  Variant='" + variant.getVariantId() + "', sourceStatus=" + variant.getSourceStatus()
                + ", scenarioType=" + variant.getScenarioType() + ", glLineType=" + variant.getGlLineType());

        ApiGeneralVoucherEditFixtures.Fixture fixture = null;
        try {
            fixture = ApiGeneralVoucherEditFixtures.create(api, empCode, variant, log, ledgerClient, subLedgerOthersClient);
            return buildRow(api, fixture, variant, log);
        } catch (RuntimeException | AssertionError e) {
            if (fixture != null) ApiGeneralVoucherEditFixtures.discard(api, fixture);
            String why = e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.error("[API-Edit] Could not build row for variant '{}': {}", variant.getVariantId(), why);
            log.add("  FIXTURE FAILURE — " + why);
            return failureRow(variant, log, "Fixture for variant '" + variant.getVariantId() + "' could not be built via the API — " + why);
        }
    }

    /** A row that keeps the variant visible in the report and fails by name when the test starts. */
    private static MergedFinanceGeneralVoucherData failureRow(GeneralVoucherEditVariant variant, List<String> log, String message) {
        return MergedFinanceGeneralVoucherData.builder()
                .scenarioTypeLabel(variant.getVariantId())
                .documentInformation(new DocumentInformation(variant.getVariantId(), null, LocalDate.now(), null, null, null))
                .operationMode(GeneralVoucherOperationMode.EDIT)
                .expenseIncomeDetailList(List.of())
                .siteAllocationList(List.of())
                .entryMethod(variant.getEntryMethod())
                .fixtureFailure(message)
                .dbGatheringLog(log)
                .invocationIndex(1)
                .totalInvocations(1)
                .build();
    }

    private static MergedFinanceGeneralVoucherData buildRow(GeneralVoucherApiClient api, ApiGeneralVoucherEditFixtures.Fixture f,
                                                            GeneralVoucherEditVariant variant, List<String> log) {
        GeneralVoucherRootView view = f.view();
        List<GeneralVoucherDetailLine> lines = view.getExpenseIncomeDetail();
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("rootview of the created voucher " + f.docScheme() + " has no expenseIncomeDetail lines");
        }
        String workDate = GeneralVoucherApiClient.workDate(LocalDate.now());
        int siteCode = (int) f.site().getSiteCode();
        int ou = (int) f.ouCode();
        LocalDate vchdt = LocalDate.parse(view.getVoucherDate().substring(0, 10));

        Map<Long, GlLedgerLookupResult> glByCode = f.ledgers().stream()
                .collect(Collectors.toMap(GlLedgerLookupResult::getGlCode, g -> g, (a, b) -> a));

        List<ExpenseIncomeDetail> rows = new ArrayList<>();
        List<SiteAllocation> allocations = new ArrayList<>();
        Map<Long, List<SubLedgerLookupResult>> slCache = new java.util.HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            GeneralVoucherDetailLine l = lines.get(i);
            GlLedgerLookupResult gl = glByCode.get(l.getGlCode());
            String slName = null;
            String slId = null;
            if (l.getSlCode() != null && l.getSlCode() != 0) {
                SubLedgerLookupResult sl = slCache.computeIfAbsent(l.getGlCode(),
                                g -> api.fetchSubLedgers(g, siteCode, f.empCode(), ou, workDate)).stream()
                        .filter(s -> s.getSlCode() == l.getSlCode()).findFirst().orElse(null);
                slName = sl != null ? sl.getSlName() : l.getSlName();
                slId = sl != null ? sl.getSlid() : null;
            }
            boolean debit = l.getExpense() != 0;
            BigDecimal amount = BigDecimal.valueOf(debit ? l.getExpense() : l.getIncome()).setScale(2, java.math.RoundingMode.HALF_UP);
            String lineNo = String.valueOf(i + 1);
            ExpenseIncomeDetail row = new ExpenseIncomeDetail(null, lineNo, l.getGlName(), slName, slId,
                    l.getLedgerNarration() == null || l.getLedgerNarration().isBlank() ? null : l.getLedgerNarration(),
                    "Y".equalsIgnoreCase(l.getIsReversal()) ? "Yes" : "No",
                    debit ? amount : null, debit ? null : amount);
            if (gl != null) {
                try {
                    row.setResolvedGlType(GlLineType.fromFlags(gl.isSiteApplicable(), gl.isSubLedgerApplicable()));
                } catch (IllegalArgumentException unmodeled) {
                    Log.warn("[ApiGeneralVoucherEditDataSource] LineNo='{}' GL '{}' has an unmodeled GlLineType flag combination — "
                            + "leaving resolvedGlType null. {}", lineNo, l.getGlName(), unmodeled.getMessage());
                }
                try {
                    row.setResolvedAccountClass(GlAccountType.fromCode(gl.getType()));
                } catch (IllegalArgumentException e) {
                    Log.warn("[ApiGeneralVoucherEditDataSource] LineNo='{}' GL '{}' has an unrecognized type '{}'.", lineNo, l.getGlName(), gl.getType());
                }
            }
            rows.add(row);
            if (row.getResolvedGlType() != null && row.getResolvedGlType().isSiteApplicable() && l.getCostCenterAllocations() != null) {
                for (CostCenterAllocation a : l.getCostCenterAllocations()) {
                    SiteAllocation sa = new SiteAllocation();
                    sa.setLineNo(lineNo);
                    sa.setSiteName(a.getName());
                    sa.setAmount(BigDecimal.valueOf(a.getAmount()).setScale(2, java.math.RoundingMode.HALF_UP));
                    allocations.add(sa);
                }
            }
        }
        log.add("  Resolved " + rows.size() + " existing line(s), " + allocations.size() + " site allocation(s) from rootview");

        DetailLineEditTarget editedDetailLine = null;
        if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.DETAIL_LINE) {
            editedDetailLine = buildEditedDetailLine(api, f, variant, rows, allocations, log);
        }

        EditDates dates = new EditDates(api, f, view, vchdt);

        PaymentInfoEditTarget paymentEditTarget = null;
        if (variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO) {
            paymentEditTarget = buildPaymentEditTarget(api, variant, view, lines.get(0), vchdt, dates, workDate, f.site().getSiteCode(), f.empCode(), log);
        }

        String releaseStatus = view.isPosted() ? "P" : "U";
        String reference = blankToNull(view.getReference());
        String remarks = blankToNull(view.getRemarks());
        return GeneralVoucherEditRowAssembler.assemble("[API-Edit]", variant, f.vchcode(),
                new GeneralVoucherEditRowAssembler.EditHeader(f.docScheme(), releaseStatus, vchdt, reference, remarks),
                view.getOwnerSite() != null ? view.getOwnerSite().getName() : f.site().getSiteName(),
                view.getCashBankLedger() != null ? view.getCashBankLedger().getGlName() : null,
                rows, allocations, editedDetailLine, paymentEditTarget, dates::resolve, log);
    }

    // ───────────────────────────── edit dates ─────────────────────────────

    /**
     * The new document date per {@link VoucherEditDateVariant}, derived from the application's own voucher-date validation (there is no
     * accounting-year API): the accepted range around the voucher's date IS its accounting year (confirmed live — the validator, given the
     * voucher's current year, rejects the day outside it). Probed once, lazily, and only for variants that change the date.
     */
    private static final class EditDates {
        private final GeneralVoucherApiClient api;
        private final ApiGeneralVoucherEditFixtures.Fixture fixture;
        private final GeneralVoucherRootView view;
        private final LocalDate vchdt;
        private ValidDateRangeFinder.Range range;

        EditDates(GeneralVoucherApiClient api, ApiGeneralVoucherEditFixtures.Fixture fixture, GeneralVoucherRootView view, LocalDate vchdt) {
            this.api = api;
            this.fixture = fixture;
            this.view = view;
            this.vchdt = vchdt;
        }

        ValidDateRangeFinder.Range range() {
            if (range == null) {
                range = api.findValidVoucherDateRange(fixture.empCode(), vchdt, fixture.ouCode(), fixture.vchcode(), view.getYCode());
                if (!range.fullyBounded()) {
                    throw new IllegalStateException("could not find the accounting-year edges for " + fixture.docScheme() + " by probing "
                            + "validateVoucherDate: " + range);
                }
            }
            return range;
        }

        LocalDate resolve(VoucherEditDateVariant variant) {
            ValidDateRangeFinder.Range r = range();
            LocalDate today = LocalDate.now();
            return switch (variant) {
                case FIRST_DAY -> r.first();
                case LAST_DAY -> r.last();
                case RANDOM_MID -> {
                    LocalDate lower = r.first().plusDays(1);
                    LocalDate upper = (r.last().isBefore(today) ? r.last() : today).minusDays(1);
                    yield upper.isBefore(lower) ? lower : lower.plusDays(ThreadLocalRandom.current().nextLong(upper.toEpochDay() - lower.toEpochDay() + 1));
                }
                // Deterministically just outside the year — the application must reject it.
                case CROSS_YEAR_INVALID -> r.last().plusDays(1);
                case FUTURE_DATE -> {
                    LocalDate lower = today.plusDays(1);
                    if (lower.isAfter(r.last())) {
                        throw new IllegalStateException("accounting year ends " + r.last() + ", so no future date inside it exists for "
                                + "FUTURE_DATE (today=" + today + ")");
                    }
                    yield lower.plusDays(ThreadLocalRandom.current().nextLong(r.last().toEpochDay() - lower.toEpochDay() + 1));
                }
            };
        }
    }

    // ───────────────────────────── detail-line edits ─────────────────────────────

    private static DetailLineEditTarget buildEditedDetailLine(GeneralVoucherApiClient api, ApiGeneralVoucherEditFixtures.Fixture f,
                                                              GeneralVoucherEditVariant variant, List<ExpenseIncomeDetail> rows,
                                                              List<SiteAllocation> allocations, List<String> log) {
        DetailLineEditTarget.EditShape shape = variant.getDetailLineEditShape() != null
                ? variant.getDetailLineEditShape() : DetailLineEditTarget.EditShape.IN_PLACE;
        return switch (shape) {
            case IN_PLACE -> DbGeneralVoucherEditDataSource.buildInPlaceEdit(variant, rows, log);
            case GL_TYPE_TRANSITION -> buildGlTypeTransition(f, variant, rows, log);
            case ADD_NEW_LINE -> buildAddNewLine(f, variant, rows, log);
            case SITE_SPLIT_ONLY -> buildSiteSplitOnly(api, f, variant, rows, allocations, log);
        };
    }

    private static Set<String> glNamesInUse(List<ExpenseIncomeDetail> rows) {
        return rows.stream().map(ExpenseIncomeDetail::getGeneralLedger).collect(Collectors.toSet());
    }

    private static DetailLineEditTarget buildGlTypeTransition(ApiGeneralVoucherEditFixtures.Fixture f, GeneralVoucherEditVariant variant,
                                                              List<ExpenseIncomeDetail> rows, List<String> log) {
        ExpenseIncomeDetail existing = rows.stream().filter(r -> r.getResolvedGlType() == variant.getGlLineType()).findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: created voucher has no line of GlLineType="
                        + variant.getGlLineType() + " for variant '" + variant.getVariantId() + "'."));
        GlAccountType targetClass = variant.getTargetAccountClass();
        if (targetClass == null) {
            throw new IllegalStateException("variant '" + variant.getVariantId() + "' is GL_TYPE_TRANSITION but declares no targetAccountClass.");
        }
        Set<String> inUse = glNamesInUse(rows);
        GlLedgerLookupResult target = f.ledgers().stream()
                .filter(g -> "G".equalsIgnoreCase(g.getSourceType()) && targetClass.code().equalsIgnoreCase(g.getType()))
                .filter(g -> g.isSubLedgerApplicable() == variant.getGlLineType().isSubLedgerApplicable()
                        && g.isSiteApplicable() == variant.getGlLineType().isSiteApplicable())
                .filter(g -> !inUse.contains(g.getGlName()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no General ledger of type=" + targetClass
                        + " with the same site/sub-ledger shape is unused on this voucher, for variant '" + variant.getVariantId() + "'."));

        boolean debit = existing.isDebit();
        BigDecimal newAmount = (debit ? existing.getExpenseDr() : existing.getIncomeCr()).add(DbGeneralVoucherEditDataSource.AMOUNT_DELTA);
        ExpenseIncomeDetail newValues = new ExpenseIncomeDetail(null, existing.getLineNo(), target.getGlName(), null, null,
                existing.getLedgerNarration(), existing.getIsReversal(), debit ? newAmount : null, debit ? null : newAmount);
        newValues.setResolvedGlType(variant.getGlLineType());
        try {
            newValues.setResolvedAccountClass(GlAccountType.fromCode(target.getType()));
        } catch (IllegalArgumentException e) {
            Log.warn("[ApiGeneralVoucherEditDataSource] Target GL '{}' has an unrecognized type — leaving resolvedAccountClass null.", target.getGlName());
        }
        log.add("  GL-type transition: LineNo='" + existing.getLineNo() + "' GL '" + existing.getGeneralLedger() + "' → '" + target.getGlName()
                + "' (account class " + targetClass + "), new amount=" + newAmount);
        return DetailLineEditTarget.builder().lineNo(existing.getLineNo()).beforeRow(existing).newValues(newValues)
                .shape(DetailLineEditTarget.EditShape.GL_TYPE_TRANSITION).build();
    }

    private static DetailLineEditTarget buildAddNewLine(ApiGeneralVoucherEditFixtures.Fixture f, GeneralVoucherEditVariant variant,
                                                        List<ExpenseIncomeDetail> rows, List<String> log) {
        Set<String> inUse = glNamesInUse(rows);
        GlLedgerLookupResult newGl = f.ledgers().stream()
                .filter(g -> "G".equalsIgnoreCase(g.getSourceType()) && !g.isSiteApplicable() && !g.isSubLedgerApplicable())
                .filter(g -> !inUse.contains(g.getGlName()))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no unused TYPE1_NONE General ledger available to add as a "
                        + "new line, for variant '" + variant.getVariantId() + "'."));
        String newLineNo = String.valueOf(rows.size() + 1);
        BigDecimal amount = DbGeneralVoucherEditDataSource.AMOUNT_DELTA;
        ExpenseIncomeDetail newRow = new ExpenseIncomeDetail(null, newLineNo, newGl.getGlName(), null, null, FakerUtils.randomRemarks(), "No",
                amount, null);
        newRow.setResolvedGlType(GlLineType.TYPE1_NONE);
        try {
            newRow.setResolvedAccountClass(GlAccountType.fromCode(newGl.getType()));
        } catch (IllegalArgumentException e) {
            Log.warn("[ApiGeneralVoucherEditDataSource] New line's GL '{}' has an unrecognized type — leaving resolvedAccountClass null.", newGl.getGlName());
        }
        log.add("  Add new line: LineNo='" + newLineNo + "' GL='" + newGl.getGlName() + "', amount=" + amount + " Dr");
        return DetailLineEditTarget.builder().lineNo(newLineNo).beforeRow(null).newValues(newRow)
                .shape(DetailLineEditTarget.EditShape.ADD_NEW_LINE).build();
    }

    private static DetailLineEditTarget buildSiteSplitOnly(GeneralVoucherApiClient api, ApiGeneralVoucherEditFixtures.Fixture f,
                                                           GeneralVoucherEditVariant variant, List<ExpenseIncomeDetail> rows,
                                                           List<SiteAllocation> allocations, List<String> log) {
        ExpenseIncomeDetail existing = rows.stream()
                .filter(r -> r.getResolvedGlType() == variant.getGlLineType())
                .filter(r -> allocations.stream().anyMatch(a -> a.getLineNo().equals(r.getLineNo())))
                .findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: created voucher has no " + variant.getGlLineType()
                        + " line with an existing site allocation, for variant '" + variant.getVariantId() + "'."));
        List<SiteAllocation> before = allocations.stream().filter(a -> a.getLineNo().equals(existing.getLineNo())).collect(Collectors.toList());

        GeneralVoucherDetailLine apiLine = f.view().getExpenseIncomeDetail().get(Integer.parseInt(existing.getLineNo()) - 1);
        Set<String> allocated = before.stream().map(SiteAllocation::getSiteName).filter(java.util.Objects::nonNull).map(String::trim)
                .collect(Collectors.toSet());
        String workDate = GeneralVoucherApiClient.workDate(LocalDate.now());
        List<CostCenterSiteLookupResult> eligible = api.fetchCostCenterSites(apiLine.getGlCode(), apiLine.getSlCode() == null || apiLine.getSlCode() == 0
                ? null : apiLine.getSlCode(), (int) f.ouCode(), (int) f.site().getSiteCode(), f.empCode(), workDate, 20);
        CostCenterSiteLookupResult newSite = eligible.stream().filter(s -> s.getName() != null && !allocated.contains(s.getName().trim())).findFirst()
                .orElseThrow(() -> new SkipException("Skipped due to lack of data: no eligible Site (via the cost-centre lookup) beyond the "
                        + allocated.size() + " already allocated for LineNo='" + existing.getLineNo() + "', for variant '" + variant.getVariantId() + "'."));

        SiteAllocation moveFrom = before.get(0);
        BigDecimal moveFromAmount = moveFrom.getAmount() != null ? moveFrom.getAmount() : BigDecimal.ZERO;
        BigDecimal delta = DbGeneralVoucherEditDataSource.AMOUNT_DELTA.min(moveFromAmount);
        if (delta.signum() <= 0) {
            throw new SkipException("Skipped due to lack of data: LineNo='" + existing.getLineNo() + "' first site allocation ('"
                    + moveFrom.getSiteName() + "') has no positive amount to redistribute, for variant '" + variant.getVariantId() + "'.");
        }
        List<SiteAllocation> after = new ArrayList<>();
        for (SiteAllocation a : before) {
            SiteAllocation copy = new SiteAllocation();
            copy.setLineNo(a.getLineNo());
            copy.setSiteName(a.getSiteName());
            copy.setAmount(a == moveFrom ? moveFromAmount.subtract(delta) : a.getAmount());
            after.add(copy);
        }
        SiteAllocation added = new SiteAllocation();
        added.setLineNo(existing.getLineNo());
        added.setSiteName(newSite.getName());
        added.setAmount(delta);
        after.add(added);
        log.add("  Site-split-only: LineNo='" + existing.getLineNo() + "' moving " + delta + " from '" + moveFrom.getSiteName()
                + "' to '" + newSite.getName() + "' — total amount unchanged");

        ExpenseIncomeDetail newValues = new ExpenseIncomeDetail(null, existing.getLineNo(), existing.getGeneralLedger(), existing.getSubLedger(),
                existing.getSubLedgerId(), existing.getLedgerNarration(), existing.getIsReversal(), existing.getExpenseDr(), existing.getIncomeCr());
        newValues.setResolvedGlType(existing.getResolvedGlType());
        newValues.setResolvedAccountClass(existing.getResolvedAccountClass());
        return DetailLineEditTarget.builder().lineNo(existing.getLineNo()).beforeRow(existing).newValues(newValues)
                .shape(DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY).beforeSiteAllocations(before).newSiteAllocations(after).build();
    }

    // ───────────────────────────── payment-info edits ─────────────────────────────

    private static PaymentInfoEditTarget buildPaymentEditTarget(GeneralVoucherApiClient api, GeneralVoucherEditVariant variant,
                                                                GeneralVoucherRootView view, GeneralVoucherDetailLine first, LocalDate vchdt,
                                                                EditDates dates, String workDate, long ownerSiteCode, long empCode,
                                                                List<String> log) {
        PaymentSelectionType beforeMode = PaymentSelectionType.fromDbCode(first.getPaymentMode());
        String beforeDate = first.getInstrumentDate() == null || first.getInstrumentDate().length() < 10 ? null
                : first.getInstrumentDate().substring(0, 10);
        PaymentInformation beforePayment = new PaymentInformation(variant.getVariantId(), "CASHBANK", beforeMode,
                blankToNull(first.getInstrumentNumber()), beforeDate, blankToNull(first.getDrawnOn()), null);

        PaymentInfoEditTarget.PaymentModeShape shape = variant.getPaymentInfoEditShape() != null
                ? variant.getPaymentInfoEditShape() : PaymentInfoEditTarget.PaymentModeShape.SAME_MODE_NEW_INSTRUMENT;

        String futureInstrumentDate = null;
        if (variant.getDateScenario() != null && variant.getDateScenario().futureDate()) {
            LocalDate candidate = vchdt.plusDays(5);
            LocalDate cap = dates.range().last();
            futureInstrumentDate = (candidate.isAfter(cap) ? cap : candidate).format(DateUtils.UI_DATE_FORMAT);
        }

        PaymentInformation newPayment;
        ChequeInstrumentResolver.ChequeBankMatch newBankMatch;
        Long newBookcode;
        if (shape == PaymentInfoEditTarget.PaymentModeShape.CHEQUE_TO_NEFT) {
            String instrumentDate = futureInstrumentDate != null ? futureInstrumentDate : vchdt.format(DateUtils.UI_DATE_FORMAT);
            newPayment = new PaymentInformation(variant.getVariantId(), "CASHBANK", PaymentSelectionType.NEFT_RTGS,
                    PaymentReferenceGenerator.generateNEFT(), instrumentDate, null, null);
            newBankMatch = null;
            newBookcode = null;
        } else {
            long bankGl = view.getCashBankLedger().getGlCode();
            var cheque = ApiGeneralVoucherEditFixtures.pickChequeOrBackfill(api, bankGl, ownerSiteCode, empCode, workDate,
                            first.getInstrumentNumber() == null ? Set.<String>of() : new HashSet<>(List.of(first.getInstrumentNumber())))
                    .orElseThrow(() -> new SkipException("Skipped due to lack of data: no further registered, unused cheque exists for Bank ledger '"
                            + view.getCashBankLedger().getGlName() + "' to change the instrument to, for variant '" + variant.getVariantId() + "'."));
            newPayment = new PaymentInformation(variant.getVariantId(), "CASHBANK", PaymentSelectionType.CHEQUE,
                    cheque.getChequeNumber(), futureInstrumentDate, blankToNull(first.getDrawnOn()), null);
            newBankMatch = ChequeInstrumentResolver.ChequeBankMatch.SAME_BANK;
            newBookcode = cheque.getBookCode();
        }
        log.add("  Payment-Info edit (" + shape + "): '" + beforePayment.getPaymentMode() + " / " + beforePayment.getInstrumentNo() + "' -> '"
                + newPayment.getPaymentMode() + " / " + newPayment.getInstrumentNo() + "' (newBankMatch=" + newBankMatch + ", newBookcode=" + newBookcode + ")");

        return PaymentInfoEditTarget.builder()
                .beforePayment(beforePayment)
                .newPayment(newPayment)
                .beforeBankMatch(beforeMode == PaymentSelectionType.CHEQUE ? ChequeInstrumentResolver.ChequeBankMatch.SAME_BANK : null)
                .newBankMatch(newBankMatch)
                .newBookcode(newBookcode)
                .shape(shape)
                .build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
