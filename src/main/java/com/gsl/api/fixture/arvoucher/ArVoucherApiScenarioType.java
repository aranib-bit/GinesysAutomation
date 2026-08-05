package com.gsl.api.fixture.arvoucher;

import com.gsl.api.client.ArVoucherApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.fixture.core.ScenarioKind;
import com.gsl.api.fixture.core.ScenarioOutcome;
import com.gsl.api.model.arvoucher.ArVoucherCreateRequest;
import com.gsl.api.model.arvoucher.ArVoucherCreateResponse;
import com.gsl.api.model.arvoucher.CashBankLedgerLookupResult;
import com.gsl.api.model.arvoucher.DocNumberSchemeEntry;
import com.gsl.api.model.arvoucher.ReferenceSiteLookupResult;
import com.gsl.api.model.arvoucher.SubLedgerLookupResult;
import com.gsl.api.model.udf.UdfFieldMetadata;
import com.gsl.api.validator.ArVoucherApiValidator;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.List;
import java.util.function.BiFunction;

/**
 * One constant per endpoint-variant pair for {@link ArVoucherApiClient}. Note
 * {@code resolveSubLedger} deliberately hard-fails on a classification miss (no soft fallback,
 * unlike {@code ServiceInvoiceAdhocApiClient.resolveVendor}) — its own javadoc documents this as
 * an intentional deviation, so the scenario here checks for that thrown {@link ApiException},
 * not a fallback result.
 */
public enum ArVoucherApiScenarioType {

    FETCH_DOC_NUMBER_SCHEMES_POSITIVE("docnumberschemes", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                List<DocNumberSchemeEntry> schemes = client.fetchDocNumberSchemes(ctx.getOwnerSiteCode(), workDate);
                return schemes != null
                        ? ScenarioOutcome.pass("fetchDocNumberSchemes returned " + schemes.size() + " scheme(s)")
                        : ScenarioOutcome.fail("fetchDocNumberSchemes returned null for a known-valid site");
            }),

    FETCH_DOC_NUMBER_SCHEMES_NEGATIVE_INVALID_SITE("docnumberschemes", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    List<DocNumberSchemeEntry> schemes = client.fetchDocNumberSchemes(-1, workDate);
                    return (schemes == null || schemes.isEmpty())
                            ? ScenarioOutcome.pass("fetchDocNumberSchemes(-1) returned no schemes, as expected")
                            : ScenarioOutcome.fail("fetchDocNumberSchemes(-1) unexpectedly returned " + schemes.size() + " scheme(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchDocNumberSchemes(-1) rejected as expected: " + e.getMessage());
                }
            }),

    FETCH_CASH_BANK_LEDGERS_POSITIVE("ledgers/cashbankledgers", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                List<CashBankLedgerLookupResult> ledgers = client.fetchCashBankLedgers(
                        ctx.getOwnerSiteCode(), (int) ctx.getEmpCode(), ctx.getOuCode(), workDate);
                return (ledgers != null && !ledgers.isEmpty())
                        ? ScenarioOutcome.pass("fetchCashBankLedgers returned " + ledgers.size() + " ledger(s)")
                        : ScenarioOutcome.fail("fetchCashBankLedgers returned no ledgers for a known-valid site");
            }),

    FETCH_CASH_BANK_LEDGERS_NEGATIVE_INVALID_SITE("ledgers/cashbankledgers", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    List<CashBankLedgerLookupResult> ledgers = client.fetchCashBankLedgers(-1, -1, -1, workDate);
                    return (ledgers == null || ledgers.isEmpty())
                            ? ScenarioOutcome.pass("fetchCashBankLedgers(-1,-1,-1) returned no ledgers, as expected")
                            : ScenarioOutcome.fail("fetchCashBankLedgers(-1,-1,-1) unexpectedly returned " + ledgers.size() + " ledger(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchCashBankLedgers(-1,-1,-1) rejected as expected: " + e.getMessage());
                }
            }),

    FETCH_SUB_LEDGERS_POSITIVE("subledgers", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                List<SubLedgerLookupResult> subLedgers = client.fetchSubLedgers(
                        ctx.getOwnerSiteCode(), ctx.getOuCode(), (int) ctx.getEmpCode(), workDate);
                return (subLedgers != null && !subLedgers.isEmpty())
                        ? ScenarioOutcome.pass("fetchSubLedgers returned " + subLedgers.size() + " sub-ledger(s)")
                        : ScenarioOutcome.fail("fetchSubLedgers returned no sub-ledgers for a known-valid site");
            }),

    FETCH_SUB_LEDGERS_NEGATIVE_INVALID_SITE("subledgers", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    List<SubLedgerLookupResult> subLedgers = client.fetchSubLedgers(-1, -1, -1, workDate);
                    return (subLedgers == null || subLedgers.isEmpty())
                            ? ScenarioOutcome.pass("fetchSubLedgers(-1,-1,-1) returned no sub-ledgers, as expected")
                            : ScenarioOutcome.fail("fetchSubLedgers(-1,-1,-1) unexpectedly returned " + subLedgers.size() + " sub-ledger(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchSubLedgers(-1,-1,-1) rejected as expected: " + e.getMessage());
                }
            }),

    RESOLVE_SUB_LEDGER_NEGATIVE_HARD_FAIL_ON_UNKNOWN_CLASS("subledgers (resolveSubLedger hard-fail)", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    SubLedgerLookupResult sl = client.resolveSubLedger(
                            ctx.getOwnerSiteCode(), ctx.getOuCode(), (int) ctx.getEmpCode(), workDate, 999_999L);
                    return ScenarioOutcome.fail("resolveSubLedger(requiredClsCode=999999) unexpectedly resolved: " + sl
                            + " — documented behavior is a hard fail on classification miss, not a fallback");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("resolveSubLedger(requiredClsCode=999999) hard-failed as documented: " + e.getMessage());
                }
            }),

    FETCH_REFERENCE_SITES_POSITIVE("sites?target=referencesite", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                SubLedgerLookupResult subLedger = client.resolveSubLedger(
                        ctx.getOwnerSiteCode(), ctx.getOuCode(), (int) ctx.getEmpCode(), workDate, 0);
                List<ReferenceSiteLookupResult> refSites = client.fetchReferenceSites(
                        ctx.getOuCode(), subLedger.getSlCode(), ctx.getOwnerSiteCode(), workDate);
                return refSites != null
                        ? ScenarioOutcome.pass("fetchReferenceSites returned " + refSites.size() + " site(s)")
                        : ScenarioOutcome.fail("fetchReferenceSites returned null for a known-valid sub-ledger");
            }),

    FETCH_REFERENCE_SITES_NEGATIVE_INVALID_SLCODE("sites?target=referencesite", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    List<ReferenceSiteLookupResult> refSites = client.fetchReferenceSites(ctx.getOuCode(), -1, ctx.getOwnerSiteCode(), workDate);
                    return (refSites == null || refSites.isEmpty())
                            ? ScenarioOutcome.pass("fetchReferenceSites(slCode=-1) returned no sites, as expected")
                            : ScenarioOutcome.fail("fetchReferenceSites(slCode=-1) unexpectedly returned " + refSites.size() + " site(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchReferenceSites(slCode=-1) rejected as expected: " + e.getMessage());
                }
            }),

    VALIDATE_VOUCHER_DATE_POSITIVE("vouchers/ar/validations/voucherdate", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                String workDate = LocalDate.now() + "T00:00:00";
                try {
                    client.validateVoucherDate(ctx.getEmpCode(), workDate, ctx.getOuCode());
                    return ScenarioOutcome.pass("validateVoucherDate accepted today's date");
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("validateVoucherDate rejected today's date: " + e.getMessage());
                }
            }),

    VALIDATE_VOUCHER_DATE_NEGATIVE_MALFORMED_DATE("vouchers/ar/validations/voucherdate", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    client.validateVoucherDate(ctx.getEmpCode(), "not-a-real-date", ctx.getOuCode());
                    return ScenarioOutcome.fail("validateVoucherDate accepted a malformed date string");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("validateVoucherDate rejected a malformed date string, as expected: " + e.getMessage());
                }
            }),

    FETCH_UDF_METADATA_POSITIVE("udf/GetUDFMetaData", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                List<UdfFieldMetadata> metadata = client.fetchUdfMetadata("FINVCHMAIN");
                return metadata != null
                        ? ScenarioOutcome.pass("fetchUdfMetadata returned " + metadata.size() + " field(s)")
                        : ScenarioOutcome.fail("fetchUdfMetadata returned null for a known-valid table name");
            }),

    FETCH_UDF_METADATA_NEGATIVE_INVALID_TABLE("udf/GetUDFMetaData", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                try {
                    List<UdfFieldMetadata> metadata = client.fetchUdfMetadata("NO_SUCH_TABLE_XYZ");
                    return (metadata == null || metadata.isEmpty())
                            ? ScenarioOutcome.pass("fetchUdfMetadata(bogus table) returned no fields, as expected")
                            : ScenarioOutcome.fail("fetchUdfMetadata(bogus table) unexpectedly returned " + metadata.size() + " field(s)");
                } catch (ApiException e) {
                    return ScenarioOutcome.pass("fetchUdfMetadata(bogus table) rejected as expected: " + e.getMessage());
                }
            }),

    CREATE_AR_VOUCHER_POSITIVE("vouchers/ar", ScenarioKind.POSITIVE,
            (client, ctx) -> {
                try {
                    ArVoucherCreateResponse response = client.createArVoucherWithDefaults(
                            0, ctx.getOwnerSiteCode(), ctx.getOuCode(), ctx.getEmpCode(), ctx.getQuantity(), ctx.getRate());
                    ArVoucherApiValidator.validate(response);
                    return ScenarioOutcome.pass("createArVoucherWithDefaults succeeded — voucherCode=" + response.getVoucherCode());
                } catch (ApiException e) {
                    return ScenarioOutcome.fail("createArVoucherWithDefaults threw unexpectedly for valid input: " + e.getMessage());
                }
            }),

    CREATE_AR_VOUCHER_NEGATIVE_MISSING_MANDATORY_FIELDS("vouchers/ar", ScenarioKind.NEGATIVE,
            (client, ctx) -> {
                ArVoucherCreateRequest incomplete = ArVoucherCreateRequest.builder().build();
                Response response = client.createArVoucherExpectFailure(incomplete);
                boolean rejected = response.getStatusCode() >= 400
                        || Boolean.FALSE.equals(response.jsonPath().get("success"));
                return rejected
                        ? ScenarioOutcome.pass("Finance/api/vouchers/ar rejected an incomplete request as expected (HTTP "
                                + response.getStatusCode() + ")")
                        : ScenarioOutcome.fail("Finance/api/vouchers/ar unexpectedly accepted an incomplete request (HTTP "
                                + response.getStatusCode() + "): " + response.getBody().asString());
            });

    private final String endpoint;
    private final ScenarioKind kind;
    private final BiFunction<ArVoucherApiClient, ArVoucherFixtureContext, ScenarioOutcome> action;

    ArVoucherApiScenarioType(String endpoint, ScenarioKind kind,
            BiFunction<ArVoucherApiClient, ArVoucherFixtureContext, ScenarioOutcome> action) {
        this.endpoint = endpoint;
        this.kind = kind;
        this.action = action;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public ScenarioKind getKind() {
        return kind;
    }

    public ScenarioOutcome execute(ArVoucherApiClient client, ArVoucherFixtureContext ctx) {
        return action.apply(client, ctx);
    }
}
