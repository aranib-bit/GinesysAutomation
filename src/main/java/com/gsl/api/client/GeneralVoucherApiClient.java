package com.gsl.api.client;

import com.gsl.api.core.ApiClient;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateRequest;
import com.gsl.api.model.generalvoucher.GeneralVoucherCreateResponse;
import com.gsl.config.ConfigFactory;
import com.gsl.enums.GlAccountType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Finance module API client for General Voucher creation — <b>structural scaffold, not yet
 * functional</b>. Unlike {@link ArVoucherApiClient} (whose flow was reverse-engineered from a
 * captured HAR — {@code CreateARVoucherstage-ideal.ginesys.cloud4.har}), no HAR capture of a real
 * "create General Voucher" UI action exists in this codebase yet, so:
 * <ul>
 *   <li>{@link #createGeneralVoucher(GeneralVoucherCreateRequest)} posts to the placeholder path
 *       already sitting in {@code FinanceApiPattern.GENERAL_VOUCHER} ({@code Finance/api/vouchers/gv}),
 *       itself flagged {@code // ← confirm path} — likely wrong until confirmed.</li>
 *   <li>{@link #createGeneralVoucherWithDefaults} deliberately throws
 *       {@link UnsupportedOperationException} rather than guess GV's actual creation flow (its
 *       screen posts GL rows directly, with no AR-style sub-ledger/cash-bank-lookup steps — copying
 *       AR's orchestration would likely encode wrong business logic).</li>
 * </ul>
 *
 * <p>This class, {@link com.gsl.api.model.generalvoucher.GeneralVoucherCreateRequest}/
 * {@code GeneralVoucherDetailLine}/{@code GeneralVoucherCreateResponse}, and the
 * {@code GeneralVoucherApiClient} wiring into {@code ApiSessionBundle}/{@code ApiSessionFactory}
 * exist now purely so {@code GeneralVoucherApiBackfillStrategy} (and, through it,
 * {@code DataAvailabilityGuard}'s retry mechanism) has the correct shape to call into — once a real
 * HAR capture is supplied, only this class's endpoint path and
 * {@link #createGeneralVoucherWithDefaults}'s body (plus the DTOs' field lists) need correcting.
 */
public class GeneralVoucherApiClient extends ApiClient {

    /** Wraps an already-authenticated session {@code spec} — see {@link ApiClient}'s class Javadoc. */
    public GeneralVoucherApiClient(RequestSpecification spec) {
        super(spec);
    }

    /** Prefixes {@code path} with {@code financeApiBaseUri}, matching the other Finance-module voucher clients. */
    private static String url(String path) {
        return ConfigFactory.getApiConfig().financeApiBaseUri() + path;
    }

    /** POST Finance/api/vouchers/gv — the final save call. Path is provisional, see class Javadoc. */
    public GeneralVoucherCreateResponse createGeneralVoucher(GeneralVoucherCreateRequest request) {
        return post(url("/vouchers/gv"), request, GeneralVoucherCreateResponse.class);
    }

    /** Negative-scenario counterpart to {@link #createGeneralVoucher}, mirroring {@code ArVoucherApiClient.createArVoucherExpectFailure}. */
    public Response createGeneralVoucherExpectFailure(GeneralVoucherCreateRequest request) {
        return postRawExpectFailure(url("/vouchers/gv"), request);
    }

    /**
     * Orchestration entry point {@code GeneralVoucherApiBackfillStrategy} calls to create one
     * Posted General Voucher of the given account type. <b>Not implemented</b> — see class
     * Javadoc. Throws immediately so a genuine shortfall during a test run fails loudly with a
     * message pointing back here, instead of silently creating incorrect data or NPEing further
     * down {@code DataAvailabilityGuard}'s retry path.
     *
     * @param accountType the {@code fingl.type} the backfilled voucher's GL line must belong to
     */
    public GeneralVoucherCreateResponse createGeneralVoucherWithDefaults(GlAccountType accountType,
            int ownerSiteCode, int ouCode, long empCode) {
        throw new UnsupportedOperationException(
                "GeneralVoucherApiClient.createGeneralVoucherWithDefaults is a structural stub pending a HAR "
                + "capture of a real 'create General Voucher' UI action — see this class's Javadoc and the "
                + "'DB-driven RevertGeneralVoucher (with WebApi backfill)' plan's 'Known limitation' section. "
                + "accountType=" + accountType + ", ownerSiteCode=" + ownerSiteCode + ", ouCode=" + ouCode
                + ", empCode=" + empCode);
    }

    /**
     * Orchestration entry point {@code GeneralVoucherEditRevertBackfillStrategy} calls to convert
     * one Posted ({@code release_status='P'}) General Voucher to Unposted ({@code 'U'}) as a
     * data-preparation prerequisite for Edit's DB-mode "Posted source, needs revert" scenarios
     * (plan §4). <b>Not implemented</b> — same structural-stub shape and rationale as
     * {@link #createGeneralVoucherWithDefaults}: no HAR capture of a real "Revert General Voucher"
     * UI action exists yet, so this throws immediately rather than guessing the endpoint/body and
     * silently creating incorrect data.
     *
     * @param vchcode the {@code finvchmain.vchcode} of the Posted voucher to revert to Unposted
     */
    public GeneralVoucherCreateResponse revertGeneralVoucher(long vchcode) {
        throw new UnsupportedOperationException(
                "GeneralVoucherApiClient.revertGeneralVoucher is a structural stub pending a HAR capture of a "
                + "real 'Revert General Voucher' UI action — see this class's Javadoc and "
                + "GeneralVoucherEditRevertBackfillStrategy's Javadoc. vchcode=" + vchcode);
    }
}
