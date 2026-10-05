package com.gsl.api.integration;

import com.gsl.api.client.DocSchemeAdminApiClient;
import com.gsl.api.client.DocSchemeAdminApiClient.AccountingPeriod;
import com.gsl.api.client.DocSchemeAdminApiClient.NewScheme;
import com.gsl.api.client.DocSchemeAdminApiClient.SchemeRef;
import com.gsl.api.core.ApiSessionBundle;
import com.gsl.api.core.ApiSessionFactory;
import com.gsl.config.ConfigFactory;
import com.gsl.config.EnvResolver;
import com.gsl.reports.ReportLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Idempotently makes sure a dedicated small-width test document scheme exists, is tagged to the voucher
 * menu(s) and is tagged to the owner site — the three admin-screen steps recorded in the numbering-scheme
 * HARs. Running it twice changes nothing the second time.
 *
 * <p>Safety guards (both throw before any call is made):
 * <ul>
 *   <li>only docnames listed in {@code docscheme.test.names} — a real scheme such as "AR/AP Voucher"
 *       can never be created or re-tagged by this class;</li>
 *   <li>only environments listed in {@code docscheme.provision.allowed.envs}.</li>
 * </ul>
 * Site tagging re-posts the whole site entity (that is what the screen does), so it re-reads the site
 * immediately before the update, changes only {@code assignDocSchemes}, and does nothing if the scheme is
 * already tagged.
 */
public final class DocSchemeProvisioner {

    private DocSchemeProvisioner() {}

    /** One scheme to ensure: its name, number prefix, digit width and the voucher menus it serves. */
    public record Spec(String docName, String prefix, int width, List<String> menuShortcodes) {}

    /** What {@link #ensure} did; {@code lines} are narrative report lines (created / already existed per step). */
    public record Result(long docCode, boolean created, boolean menuTagged, boolean siteTagged, List<String> lines) {}

    /**
     * Opens a WEBAPI session as a user acquired from the UserList pool (the same self-acquire every other
     * API backfill uses — released again on logout), ensures every spec for {@code siteCode}, and always
     * logs out. The pool user must hold the numbering-scheme / organization-site admin permissions.
     */
    public static List<Result> ensureAll(List<Spec> specs, long siteCode) {
        assertEnvironmentAllowed();
        specs.forEach(DocSchemeProvisioner::assertTestScheme);

        ApiSessionBundle session = ApiSessionFactory.createForWebApi();
        try {
            DocSchemeAdminApiClient client = new DocSchemeAdminApiClient(session.getBaseSpec());
            List<Result> results = new ArrayList<>();
            for (Spec spec : specs) {
                results.add(ensure(client, spec, siteCode));
            }
            return results;
        } finally {
            session.logout();
        }
    }

    /** Ensures one scheme against an already-authenticated admin {@code client}. */
    public static Result ensure(DocSchemeAdminApiClient client, Spec spec, long siteCode) {
        assertEnvironmentAllowed();
        assertTestScheme(spec);
        List<String> lines = new ArrayList<>();
        String workDate = DocSchemeAdminApiClient.today();

        // 1. Scheme exists?
        SchemeRef scheme = find(client.listSchemes(workDate), spec.docName());
        boolean created = false;
        if (scheme == null) {
            AccountingPeriod period = client.fetchAccountingPeriod();
            client.createScheme(new NewScheme(spec.docName(), spec.prefix(), spec.width(),
                    "Automation test scheme — safe to fill"), period);
            scheme = find(client.listSchemes(workDate), spec.docName());
            if (scheme == null) {
                throw new IllegalStateException("Created doc scheme '" + spec.docName()
                        + "' but it is not in GetListOfDocScheme afterwards.");
            }
            created = true;
            lines.add("  Doc scheme '" + spec.docName() + "' CREATED (doccode=" + scheme.code()
                    + ", width=" + spec.width() + ", prefix='" + spec.prefix() + "')");
        } else {
            lines.add("  Doc scheme '" + spec.docName() + "' already exists (doccode=" + scheme.code() + ")");
        }

        // 2. Tagged to the voucher menus?
        Set<String> have = new LinkedHashSet<>(scheme.menuShortcodes());
        List<String> missing = spec.menuShortcodes().stream().filter(m -> !have.contains(m)).collect(Collectors.toList());
        boolean menuTagged = false;
        if (!missing.isEmpty()) {
            Set<String> all = new LinkedHashSet<>(have);
            all.addAll(missing);   // keep what is already tagged so nothing is dropped
            client.tagToMenus(scheme.code(), new ArrayList<>(all));
            menuTagged = true;
            lines.add("  Tagged to menus " + missing + " (now " + all + ")");
        } else {
            lines.add("  Already tagged to menus " + spec.menuShortcodes());
        }

        // 3. Tagged to the owner site?
        final long docCode = scheme.code();
        Map<String, Object> untagged = client.listUntaggedSchemes(siteCode, workDate).stream()
                .filter(m -> m.get("code") != null && ((Number) m.get("code")).longValue() == docCode)
                .findFirst().orElse(null);
        boolean siteTagged = false;
        if (untagged != null) {
            Map<String, Object> site = client.fetchSite(siteCode);   // fresh read right before the whole-entity update
            Map<String, Object> body = DocSchemeAdminApiClient.buildSiteUpdateBody(
                    site, client.fetchSiteUdfMetadata(), untagged);
            client.updateSite(siteCode, body);
            siteTagged = true;
            lines.add("  Tagged to site code=" + siteCode);
        } else {
            lines.add("  Already tagged to site code=" + siteCode);
        }

        lines.forEach(l -> ReportLogger.dataBackfill("[DocSchemeProvisioner]{}", l));
        return new Result(docCode, created, menuTagged, siteTagged, lines);
    }

    // ── guards / helpers ─────────────────────────────────────────────────────

    /** @return whether the active {@code -Denv} is in {@code docscheme.provision.allowed.envs}. */
    public static boolean isEnvironmentAllowed() {
        return allowedEnvironments().contains(EnvResolver.current().toLowerCase(Locale.ROOT));
    }

    /** @throws IllegalStateException unless the active {@code -Denv} is in {@code docscheme.provision.allowed.envs}. */
    public static void assertEnvironmentAllowed() {
        if (!isEnvironmentAllowed()) {
            throw new IllegalStateException("Doc-scheme provisioning/filling is not allowed in environment '"
                    + EnvResolver.current() + "' (docscheme.provision.allowed.envs=" + allowedEnvironments() + ").");
        }
    }

    private static Set<String> allowedEnvironments() {
        return Arrays.stream(ConfigFactory.getFinanceConfig().docSchemeProvisionAllowedEnvs())
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /** @throws IllegalStateException unless {@code spec.docName()} is listed in {@code docscheme.test.names}. */
    static void assertTestScheme(Spec spec) {
        assertTestSchemeName(spec.docName());
    }

    /**
     * Shared guard for everything that may create, tag or bulk-fill a scheme (this class and the k6 filler).
     *
     * @throws IllegalStateException unless {@code docName} is listed in {@code docscheme.test.names}
     */
    public static void assertTestSchemeName(String docName) {
        Set<String> testNames = Arrays.stream(ConfigFactory.getFinanceConfig().docSchemeTestNames())
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (docName == null || !testNames.contains(docName.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Refusing to touch doc scheme '" + docName
                    + "': not listed in docscheme.test.names " + testNames
                    + ". Only dedicated test schemes may be created, tagged or bulk-filled by automation.");
        }
    }

    private static SchemeRef find(List<SchemeRef> schemes, String docName) {
        return schemes.stream().filter(s -> s.docName() != null && s.docName().equalsIgnoreCase(docName))
                .findFirst().orElse(null);
    }
}
