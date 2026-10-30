package com.gsl.validation.api;

import com.gsl.api.client.GeneralVoucherApiClient;
import com.gsl.api.core.ApiException;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.api.model.generalvoucher.GeneralVoucherListItem;
import com.gsl.api.model.generalvoucher.GeneralVoucherRootView;
import com.gsl.utils.Log;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * The one WebAPI session a UI test run shares, opened lazily and only when {@code db.available=no}, to read vouchers back where a test would
 * otherwise query the database.
 *
 * <p>It is a process-wide singleton, not a session per test: every login takes a seat from the environment's concurrent-user licence and
 * holds a pool user, and the Create/Revert/Delete tests have no natural place to close a per-thread session. It acquires its OWN pool user
 * (never a browser's, whose session it would kill with an "already logged in" collision), is safe to use from every test thread, logs in
 * again once if the token has expired mid-run, and is logged out by a JVM shutdown hook.
 */
public final class NoDbApiSession {

    private static NoDbApiSession shared;

    private ApiSessionBundle session;
    private GeneralVoucherApiClient client;
    private long empCode;
    private long ouCode;

    private NoDbApiSession() {
        open();
        Runtime.getRuntime().addShutdownHook(new Thread(NoDbApiSession::shutdown, "NoDbApiSession-logout"));
    }

    /** The shared session, opened on first use. */
    public static synchronized NoDbApiSession current() {
        if (shared == null) shared = new NoDbApiSession();
        return shared;
    }

    /** Kept so tests can call it from a {@code finally}: the session is shared by the whole run, so this does nothing. */
    public static void closeQuietly() {
        // intentionally empty — see the class Javadoc; the shutdown hook logs the session out
    }

    private static synchronized void shutdown() {
        if (shared == null || shared.session == null) return;
        try {
            shared.session.logout();
        } catch (RuntimeException e) {
            Log.warn("[NoDbApiSession] logout failed: {}", e.getMessage());
        }
    }

    /**
     * Opens the session, drawing another pool user when a login fails (a pool entry with a wrong password must not fail the test — the
     * factory releases the failed user, so the next draw is a different one).
     */
    private synchronized void open() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                this.session = ApiSessionFactory.createForWebApi();
                this.client = session.getGeneralVoucherApiClient();
                this.empCode = Long.parseLong(session.getUserId());
                this.ouCode = client.fetchUserContext().getOucode();
                return;
            } catch (RuntimeException e) {
                last = e;
                Log.warn("[NoDbApiSession] API login attempt {}/3 failed: {}", attempt, e.getMessage());
            }
        }
        throw last;
    }

    /** Runs {@code call}; when the token was rejected (HTTP 401) logs in again once and retries. */
    private <T> T withRelogin(Supplier<T> call) {
        try {
            return call.get();
        } catch (ApiException e) {
            if (e.getStatusCode() != 401) throw e;
            Log.warn("[NoDbApiSession] token rejected (401) — logging in again");
            synchronized (this) {
                try {
                    session.logout();
                } catch (RuntimeException ignored) {
                    // the old session is already invalid
                }
                open();
            }
            return call.get();
        }
    }

    public GeneralVoucherApiClient client() { return client; }

    /** Runs {@code call} against the Audit API client of the shared session (retried once after a re-login on HTTP 401). */
    public <T> T audit(java.util.function.Function<com.gsl.api.client.AuditLogApiClient, T> call) {
        return withRelogin(() -> call.apply(session.getAuditLogApiClient()));
    }

    /** The voucher as the application currently holds it. */
    public GeneralVoucherRootView rootView(long vchcode) {
        return withRelogin(() -> client.fetchRootView(vchcode, ouCode, empCode));
    }

    /** The voucher as the application currently holds it, or {@code null} when it cannot be read (e.g. it was deleted). */
    public GeneralVoucherRootView rootViewOrNull(long vchcode) {
        try {
            return rootView(vchcode);
        } catch (ApiException gone) {
            return null;
        }
    }

    /** The list-API row for {@code docNo} (its {@code code} is the vchcode), or {@code null} when no voucher carries that number. */
    public GeneralVoucherListItem listRow(String docNo) {
        return withRelogin(() -> {
            var site = client.fetchUserContext().firstOwnerSite();
            long siteCode = site != null ? site.getSiteCode() : 0;
            return client.findByVoucherNumber(docNo, siteCode, empCode, ouCode, GeneralVoucherApiClient.workDate(LocalDate.now()));
        });
    }

    /** The voucher numbered {@code docNo}, or {@code null} when none exists. */
    public GeneralVoucherRootView rootViewByDocNo(String docNo) {
        GeneralVoucherListItem row = listRow(docNo);
        return row == null ? null : rootViewOrNull(row.getCode());
    }
}
