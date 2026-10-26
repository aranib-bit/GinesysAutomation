package com.gsl.utils.dataprovider.source.finance;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import com.gsl.api.model.arvoucher.DocNumberSchemeEntry;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.model.common.UserSiteContext;
import com.gsl.api.model.generalvoucher.ChequeLookupResult;
import com.gsl.api.model.generalvoucher.CostCenterAllocation;
import com.gsl.api.model.generalvoucher.CostCenterSiteLookupResult;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.api.model.generalvoucher.GeneralVoucherDetailLine;
import com.gsl.api.model.generalvoucher.GeneralVoucherDocSchemeRef;
import com.gsl.api.model.generalvoucher.GeneralVoucherOwnerSite;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.api.model.generalvoucher.GlLedgerLookupResult;
import com.gsl.api.model.udf.UdfValueEntry;
import com.gsl.enums.CashBankType;
import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
import com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher.PaymentInfoEditTarget;
import com.gsl.utils.Faker.FakerUtils;
import com.gsl.utils.Log;
import com.gsl.utils.cheque.ApiChequeResolver;
import com.gsl.utils.dataprovider.fixture.finance.GeneralVoucherEditVariant;
import com.gsl.utils.payment.PaymentReferenceGenerator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Creates, through the Finance API only, the source General Voucher one Edit variant needs — one fresh voucher per variant, never shared —
 * so {@link ApiGeneralVoucherEditDataSource} works with {@code db.available=no}. What each variant needs mirrors what
 * {@code DbGeneralVoucherEditDataSource#claimOne} looks for in the database:
 * <ul>
 *   <li>the variant's Cash/Bank type (Bank for every Payment-Info variant and {@code FUTURE_DATE_BANK}, Cash where required, else Cash with a
 *       Bank fallback);</li>
 *   <li>a line of the variant's {@link GlLineType}; the shared-glcode narration variant gets two lines on one sub-ledger-applicable GL with
 *       different sub-ledgers;</li>
 *   <li>for Payment-Info variants a Cheque (registered, unused) or NEFT/RTGS reference on the first line, per the variant's
 *       {@link PaymentInfoEditTarget.PaymentModeShape};</li>
 *   <li>the variant's source status. The application auto-posts a saved voucher whatever {@code IsMarkedForSaveAndRelease} says (confirmed
 *       live), so an Unposted source is created then reverted, and a Posted source is released if it somehow isn't Posted.</li>
 * </ul>
 */
final class ApiGeneralVoucherEditFixtures {

    private static final String MENU_DOC_SCHEME = "General Voucher";

    private ApiGeneralVoucherEditFixtures() {}

    /** The created voucher plus the context later steps need to read lookups for the same site/OU. */
    record Fixture(long vchcode, String docScheme, GeneralVoucherRootView view, UserSiteContext.Site site, long ouCode, long empCode,
                   List<GlLedgerLookupResult> ledgers) {}

    static Fixture create(GeneralVoucherApiClient api, long empCode, GeneralVoucherEditVariant variant, List<String> log) {
        LocalDate today = LocalDate.now();
        String workDate = GeneralVoucherApiClient.workDate(today);
        UserSiteContext ctx = api.fetchUserContext();

        boolean paymentVariant = variant.getScenarioType() == GeneralVoucherEditVariant.ScenarioType.PAYMENT_INFO;
        PaymentInfoEditTarget.PaymentModeShape shape = variant.getPaymentInfoEditShape();
        boolean neftBefore = paymentVariant && shape == PaymentInfoEditTarget.PaymentModeShape.NEFT_TO_CHEQUE;
        boolean chequeBefore = paymentVariant && !neftBefore;
        CashBankType wanted = variant.getRequiredCashBankType() != null ? variant.getRequiredCashBankType()
                : paymentVariant ? CashBankType.BANK : null;

        GeneralVoucherApiClient.OwnerSiteChoice choice = chooseSite(api, ctx, empCode, wanted, workDate);
        UserSiteContext.Site site = choice.site();
        int siteCode = (int) site.getSiteCode();
        int ou = (int) site.getOuCode();
        CashBankLedgerLookupResult header = chooseHeaderLedger(api, choice.ledgers(), workDate, paymentVariant);
        boolean cash = "C".equalsIgnoreCase(header.getSourceType());
        log.add("  Owner site='" + site.getSiteName() + "' (" + siteCode + "), Cash/Bank ledger='" + header.getGlName() + "' ("
                + (cash ? "Cash" : "Bank") + ")");

        List<GlLedgerLookupResult> ledgers = api.fetchLedgers(siteCode, empCode, ou, workDate);

        String paymentMode = cash ? "H" : "C";
        GeneralVoucherDetailLine firstPaymentHolder;
        List<GeneralVoucherDetailLine> lines = new ArrayList<>(buildLines(api, variant, ledgers, empCode, ou, siteCode, workDate, log));
        for (GeneralVoucherDetailLine l : lines) l.setPaymentMode(paymentMode);
        firstPaymentHolder = lines.get(0);
        if (chequeBefore) {
            ChequeLookupResult cheque = pickCheque(api, header.getGlCode(), workDate, Set.of())
                    .orElseThrow(() -> new IllegalStateException("no registered, unused cheque exists for Bank ledger '" + header.getGlName()
                            + "' (glCode=" + header.getGlCode() + ") — cannot build a Cheque-before Payment-Info voucher"));
            firstPaymentHolder.setPaymentMode("C");
            firstPaymentHolder.setBookCode(cheque.getBookCode());
            firstPaymentHolder.setInstrumentNumber(cheque.getChequeNumber());
            firstPaymentHolder.setInstrumentDate(workDate);
            log.add("  Payment: Cheque no. " + cheque.getChequeNumber() + " (book " + cheque.getBookCode() + ")");
        } else if (neftBefore) {
            String ref = PaymentReferenceGenerator.generateNEFT();
            firstPaymentHolder.setPaymentMode("R");
            firstPaymentHolder.setInstrumentNumber(ref);
            firstPaymentHolder.setInstrumentDate(workDate);
            log.add("  Payment: NEFT/RTGS reference " + ref);
        }

        List<DocNumberSchemeEntry> schemes = api.fetchDocNumberSchemes(siteCode, workDate);
        DocNumberSchemeEntry scheme = schemes.stream().filter(s -> MENU_DOC_SCHEME.equalsIgnoreCase(s.getName())).findFirst()
                .orElseGet(() -> schemes.isEmpty() ? null : schemes.get(0));
        if (scheme == null) throw new ApiException("No document scheme returned for the General Voucher menu", 0);

        List<UdfValueEntry> udf = api.fetchAndBuildUdfValues();
        api.validateVoucherDate(empCode, workDate, ou, 0);
        GeneralVoucherCreateRequest request = GeneralVoucherCreateRequest.builder()
                .empCode(empCode).connectedOUCode(ou)
                .ownerSite(GeneralVoucherOwnerSite.builder().code(siteCode).name(site.getSiteName()).siteType(site.getSiteType())
                        .shortName(site.getShortName()).ouName(site.getOuName()).ouCode(site.getOuCode()).build())
                .docScheme(GeneralVoucherDocSchemeRef.builder().code(scheme.getCode()).build())
                .cashBankLedger(header)
                .voucherDate(workDate)
                .reference(FakerUtils.randomReference())
                .remarks(FakerUtils.randomRemarks())
                .expenseIncomeDetail(lines)
                .markedForSaveAndRelease(false)
                .requestId(String.valueOf(System.nanoTime()))
                .udfDetails(udf).udfValues(udf)
                .build();
        GeneralVoucherCreateResponse created = api.createGeneralVoucher(request);
        long vchcode = created.vchcode();

        GeneralVoucherRootView view = api.fetchRootView(vchcode, ou, empCode);
        boolean wantPosted = variant.getSourceStatus() == GeneralVoucherEditVariant.SourceStatus.POSTED_NEEDS_REVERT;
        if (wantPosted && !view.isPosted()) {
            api.releaseGeneralVoucherOrThrow(vchcode, empCode, ou);
            view = api.fetchRootView(vchcode, ou, empCode);
        } else if (!wantPosted && view.isPosted()) {
            api.revertGeneralVoucherOrThrow(vchcode, empCode, ou);
            view = api.fetchRootView(vchcode, ou, empCode);
        }
        log.add("  Created voucher '" + created.getDocScheme() + "' (vchcode=" + vchcode + "), " + lines.size() + " line(s), status="
                + view.getDocumentStatus() + " (wanted " + (wantPosted ? "Posted" : "Unposted") + ")");
        Log.info("[API-Edit] Built fixture voucher {} (vchcode={}) for variant '{}'", created.getDocScheme(), vchcode, variant.getVariantId());
        return new Fixture(vchcode, created.getDocScheme(), view, site, ou, empCode, ledgers);
    }

    /** Best-effort removal of a fixture created for a variant whose row could not be completed. */
    static void discard(GeneralVoucherApiClient api, Fixture f) {
        try {
            if (f.view().isPosted()) api.revertGeneralVoucherOrThrow(f.vchcode(), f.empCode(), f.ouCode());
            api.deleteGeneralVoucherOrThrow(f.vchcode(), f.empCode(), f.ouCode());
        } catch (RuntimeException e) {
            Log.warn("[API-Edit] Could not discard incomplete fixture vchcode={}: {}", f.vchcode(), e.getMessage());
        }
    }

    /** Claims the first usable cheque leaf of {@code bankGlCode} no row has claimed yet — see {@link ApiChequeResolver}. */
    static java.util.Optional<ChequeLookupResult> pickCheque(GeneralVoucherApiClient api, long bankGlCode, String workDate,
                                                             Set<String> exclude) {
        return ApiChequeResolver.claim(api, bankGlCode, workDate, exclude);
    }

    private static boolean hasUnclaimedCheque(GeneralVoucherApiClient api, long bankGlCode, String workDate) {
        return ApiChequeResolver.hasUnclaimed(api, bankGlCode, workDate);
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static GeneralVoucherApiClient.OwnerSiteChoice chooseSite(GeneralVoucherApiClient api, UserSiteContext ctx, long empCode,
                                                                      CashBankType wanted, String workDate) {
        if (wanted != null) return api.findOwnerSiteWithCashBank(ctx, empCode, wanted.srcType(), workDate);
        try {
            return api.findOwnerSiteWithCashBank(ctx, empCode, "C", workDate);
        } catch (org.testng.SkipException noCash) {
            return api.findOwnerSiteWithCashBank(ctx, empCode, "B", workDate);
        }
    }

    /** For a Cheque-before variant, the first ledger of the site that actually has an unused cheque; otherwise the first ledger. */
    private static CashBankLedgerLookupResult chooseHeaderLedger(GeneralVoucherApiClient api, List<CashBankLedgerLookupResult> ledgers,
                                                                 String workDate, boolean needCheque) {
        if (!needCheque) return ledgers.get(0);
        for (CashBankLedgerLookupResult l : ledgers) {
            if (hasUnclaimedCheque(api, l.getGlCode(), workDate)) return l;
        }
        throw new IllegalStateException("none of the " + ledgers.size() + " Bank ledger(s) at the chosen site has a registered, unused cheque");
    }

    private static List<GeneralVoucherDetailLine> buildLines(GeneralVoucherApiClient api, GeneralVoucherEditVariant variant,
                                                             List<GlLedgerLookupResult> ledgers, long empCode, int ou, int siteCode,
                                                             String workDate, List<String> log) {
        GlAccountType accountType = GlAccountType.ASSET;
        boolean debit = accountType.allowsBothSides() || accountType.isDebitOnly();
        double amount = variant.getDetailLineEditShape() == com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher
                .DetailLineEditTarget.EditShape.SITE_SPLIT_ONLY ? 1000 : 100;

        List<GlLedgerLookupResult> general = ledgers.stream()
                .filter(l -> accountType.code().equalsIgnoreCase(l.getType()) && "G".equalsIgnoreCase(l.getSourceType())).toList();

        if (variant.isRequireSharedGlcodeSibling()) {
            for (GlLedgerLookupResult gl : general) {
                if (!gl.isSubLedgerApplicable() || gl.isSiteApplicable()) continue;   // the unmodeled sl=Y/site=N shape this variant targets
                List<SubLedgerLookupResult> sls = api.fetchSubLedgers(gl.getGlCode(), siteCode, empCode, ou, workDate);
                if (sls == null || sls.size() < 2) continue;
                log.add("  Two lines on GL '" + gl.getGlName() + "' with sub-ledgers '" + sls.get(0).getSlName() + "' and '"
                        + sls.get(1).getSlName() + "' (shared-glcode narration variant)");
                return List.of(line(gl, sls.get(0), debit, amount, null), line(gl, sls.get(1), debit, amount, null));
            }
            throw new IllegalStateException("no Asset General ledger with Sub Ledger Applicable=Y/Site Applicable=N and at least two sub-ledgers "
                    + "exists at site " + siteCode + " (shared-glcode narration variant)");
        }

        GlLineType type = variant.getGlLineType() != null ? variant.getGlLineType() : GlLineType.TYPE1_NONE;
        boolean wantSite = type.isSiteApplicable();
        boolean wantSl = type.isSubLedgerApplicable();
        // HEADER/PAYMENT variants (no line type of their own): a Type1 line, else a Type2 one, like createGeneralVoucherWithDefaults.
        boolean flexible = variant.getGlLineType() == null;
        for (int pass = 0; pass < (flexible ? 2 : 1); pass++) {
            boolean site = flexible ? pass == 1 : wantSite;
            for (GlLedgerLookupResult gl : general) {
                if (gl.isSiteApplicable() != site || gl.isSubLedgerApplicable() != (flexible ? false : wantSl)) continue;
                SubLedgerLookupResult sl = null;
                if (wantSl && !flexible) {
                    List<SubLedgerLookupResult> sls = api.fetchSubLedgers(gl.getGlCode(), siteCode, empCode, ou, workDate);
                    if (sls == null || sls.isEmpty()) continue;
                    sl = sls.get(0);
                }
                List<CostCenterAllocation> allocations = null;
                if (site) {
                    List<CostCenterSiteLookupResult> sites = api.fetchCostCenterSites(gl.getGlCode(), sl == null ? null : sl.getSlCode(), ou,
                            siteCode, empCode, workDate, 1);
                    if (sites == null || sites.isEmpty()) continue;
                    allocations = List.of(sites.get(0).toAllocation(amount, ou));
                }
                log.add("  One " + (site ? (sl != null ? "Type3" : "Type2") : "Type1") + " line on GL '" + gl.getGlName() + "'"
                        + (sl != null ? ", sub-ledger '" + sl.getSlName() + "'" : "")
                        + (allocations != null ? ", site '" + allocations.get(0).getName() + "'" : ""));
                return List.of(line(gl, sl, debit, amount, allocations));
            }
        }
        throw new IllegalStateException("no Asset General ledger of shape " + type + " (site=" + wantSite + ", subLedger=" + wantSl
                + ") with the lookups it needs exists at site " + siteCode);
    }

    private static GeneralVoucherDetailLine line(GlLedgerLookupResult gl, SubLedgerLookupResult sl, boolean debit, double amount,
                                                 List<CostCenterAllocation> allocations) {
        return GeneralVoucherDetailLine.builder()
                .glCode(gl.getGlCode()).glName(gl.getGlName()).groupName(gl.getGroupName())
                .siteApplicable(allocations == null ? "N" : "Y")
                .slCode(sl == null ? null : sl.getSlCode()).slName(sl == null ? "" : sl.getSlName())
                .expense(debit ? amount : 0).income(debit ? 0 : amount)
                .costCenterAllocations(allocations)
                .build();
    }
}
