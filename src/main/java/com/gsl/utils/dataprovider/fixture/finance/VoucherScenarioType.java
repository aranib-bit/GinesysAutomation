package com.gsl.utils.dataprovider.fixture.finance;

import com.gsl.flow.Finance.Operations.Common.PostSaveDialogHandler;
import com.gsl.pages.pagecomponent.Finance.Common.CashBankParent;
import com.gsl.testdata.InvocationData;
import com.gsl.testdata.dto.VoucherReportData;

/**
 * Common contract every voucher domain's "ScenarioType" enum must implement — pairs loading a
 * scenario's test data from live DB references with executing its UI behavior, so this cannot
 * be quietly bypassed the way {@code GeneralVoucherScenarioType} previously was (it carried only
 * a template loader, no {@link #execute}; its own Javadoc predicted a behavior pair would be
 * added "when actually needed", but when GV's negative/boundary scenarios genuinely needed one,
 * they were instead wired through a parallel, ad hoc raw {@code @DataProvider} tuple in
 * {@code CreateGeneralVoucherNegativeBoundary}, bypassing the enum entirely). Implementing this
 * interface makes that impossible going forward: a domain's `ScenarioType` enum simply will not
 * compile without both methods.
 *
 * <p>Package placement: this lives next to the three enums it constrains
 * ({@code ApVoucherScenarioType}/{@code ArVoucherScenarioType}/{@code GeneralVoucherScenarioType}),
 * not in a generic {@code com.gsl.utils.*} package — per this project's Utility Layering
 * Convention ({@code docs/conventions/utility-layering.md}), a class whose type bounds are
 * permanently Finance-voucher-specific (a document header, sub-ledger/detail rows, a Cash/Bank
 * page, post-save dialog handling) is domain-adjacent scaffolding, not generic mechanics — the
 * same category as {@code IVoucherListColumn} in {@code com.gsl.pages.enums.Finance.Operations.Common}.
 *
 * @param <D> the domain's merged test-data DTO (e.g. {@code MergedFinanceApVoucherData}) — must
 *            carry a document header/detail rows ({@link InvocationData}), support post-save
 *            dialog dispatch ({@link PostSaveDialogHandler.PostSaveSpec}), and be reportable
 *            ({@link VoucherReportData}) — the exact 3-way intersection all three domains'
 *            DTOs already implement, verified before this interface was introduced.
 * @param <P> the domain's Add-page object (e.g. {@code ApVoucherAdd}) — must be a
 *            {@link CashBankParent} (a Finance voucher Add page with a Cash/Bank concept);
 *            currently an empty marker interface, kept that way since nothing here needs to
 *            call a method on {@code P} generically.
 * @param <R> the domain's live-DB-reference-values carrier (e.g. {@code ApVoucherLiveDbRefs}) —
 *            deliberately unbounded: each domain's refs type has disjoint fields from the
 *            others' (AP/AR/GV each need genuinely different live values), so no reasonable
 *            common supertype exists to bound this against.
 */
public interface VoucherScenarioType<
        D extends InvocationData & PostSaveDialogHandler.PostSaveSpec & VoucherReportData,
        P extends CashBankParent,
        R> {

    /** Builds this scenario's test data from live reference values and stamps its scenario type
     *  onto the returned object so later code (e.g. {@link #execute}) can dispatch on it. */
    D loadData(R refs);

    /** Runs this scenario's UI interaction + assertions against {@code page}, using {@code data}.
     *  Returns whether the scenario completed as expected — every implementation is a fully
     *  self-contained "do everything, return pass/fail" unit (fill form, save, handle post-save
     *  dialogs, verify, DB-validate), matching how the existing AP/AR negative-boundary behaviors
     *  already work. */
    boolean execute(D data, P page);
}
