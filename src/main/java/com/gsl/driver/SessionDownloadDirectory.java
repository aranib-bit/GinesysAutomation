package com.gsl.driver;

import com.gsl.utils.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Per-{@link PooledSession} browser download directory. Chrome's {@code download.default_directory}
 * preference is fixed for the lifetime of the browser process at launch time — it has nothing to
 * do with which thread happens to be bound to that session later. So the directory is assigned
 * ONCE, at session creation ({@link SessionPool#createSession}), stored on the {@link PooledSession}
 * itself, and re-published into this thread-local on every borrow/rebind
 * ({@link SessionPool#bindToCurrentThread}) — that is what lets a session borrowed on a different
 * thread than the one that created it still resolve the correct directory.
 *
 * <p>{@link BrowserOptions#chrome()}/{@code edge()} read {@link #current()} at driver-launch time.
 * A lazily-assigned fallback covers driver creation paths outside {@code SessionPool} (Selenium
 * Grid / cloud providers, ad hoc local runs) so they never NPE — those directories simply won't be
 * watched by any Account-Class Export-to-Excel test, which is fine since that test only runs
 * against pooled local sessions today.
 */
public final class SessionDownloadDirectory {

    private static final ThreadLocal<Path> CURRENT = new ThreadLocal<>();

    private SessionDownloadDirectory() {}

    /** Creates a fresh, session-scoped directory and binds it to the calling thread. */
    public static Path assignNew(int sessionId) {
        Path dir = Paths.get(System.getProperty("user.dir"), "target", "downloads", "session-" + sessionId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            Log.warn("[SessionDownloadDirectory] Could not create '{}': {}", dir, e.getMessage());
        }
        CURRENT.set(dir);
        return dir;
    }

    /** Re-publishes a previously-assigned directory onto the calling (borrowing) thread. */
    public static void set(Path dir) {
        CURRENT.set(dir);
    }

    /** Unbinds the calling thread's assigned directory (e.g. on session unbind); a later {@link #current()} call will re-derive a fallback. */
    public static void clear() {
        CURRENT.remove();
    }

    /** Returns the current thread's assigned directory, lazily creating a fallback if unset. */
    public static Path current() {
        Path dir = CURRENT.get();
        if (dir == null) {
            dir = Paths.get(System.getProperty("user.dir"), "target", "downloads",
                    "adhoc-" + Thread.currentThread().getId());
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                Log.warn("[SessionDownloadDirectory] Could not create fallback dir '{}': {}", dir, e.getMessage());
            }
            CURRENT.set(dir);
        }
        return dir;
    }
}
