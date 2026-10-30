package com.gsl.api.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.client.DocSchemeAdminApiClient;
import com.gsl.api.client.DocSchemeAdminApiClient.AccountingPeriod;
import com.gsl.api.client.DocSchemeAdminApiClient.SchemeRef;
import com.gsl.api.integration.DocSchemeProvisioner;
import com.gsl.api.integration.DocSchemeProvisioner.Spec;
import com.gsl.api.webapi.factory.DocSchemeRequestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Doc scheme admin through k6: creates the missing test schemes and tags them to their voucher menus, the two steps that are independent per scheme
 * and so can run in parallel. The same guards as {@link DocSchemeProvisioner} apply before anything is posted (allowed environment, only names listed
 * in {@code docscheme.test.names}), so a real scheme can never be created or re-tagged here. Tagging a scheme to a site stays in
 * {@link DocSchemeProvisioner}: it re-posts the whole site entity, a read-modify-write that must not run concurrently.
 *
 * <p>Both calls answer without the usual envelope ({@code {"result":true}}, no code), so the scheme codes are found by listing between the two
 * runs and the outcome is confirmed by listing again.
 */
public final class DocSchemeBulkProvisioner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** @param ready how many of the requested specs now exist with all their menus tagged */
    public record Result(K6Result createRun, K6Result tagRun, int requested, int ready) {
        /** Every requested scheme exists and is menu-tagged, and each run that was needed passed. */
        public boolean complete() {
            return ready == requested && (createRun == null || createRun.passed()) && (tagRun == null || tagRun.passed());
        }
    }

    private DocSchemeBulkProvisioner() {}

    /**
     * @throws IllegalStateException when the environment is not allowed or a spec's name is not a configured test scheme (nothing is posted then)
     */
    public static Result ensureCreatedAndMenuTagged(List<Spec> specs, int vus, String runId, DocSchemeAdminApiClient client, String tenant,
                                                    Consumer<String> lineHandler) {
        DocSchemeProvisioner.assertEnvironmentAllowed();
        specs.forEach(s -> DocSchemeProvisioner.assertTestSchemeName(s.docName()));
        String workDate = DocSchemeAdminApiClient.today();

        K6Result createRun = null;
        List<Spec> toCreate = missingSchemes(specs, client.listSchemes(workDate));
        if (!toCreate.isEmpty()) {
            AccountingPeriod period = client.fetchAccountingPeriod();
            Path file = write("docSchemesCreate", "CREATE", "CreateDocScheme", "result-only", runId, createBodies(toCreate, period));
            createRun = run(file, toCreate.size(), vus, tenant, lineHandler);
            if (createRun.passed()) BulkDataFiles.deleteQuietly(file);
        }

        K6Result tagRun = null;
        List<List<Map<String, Object>>> tagBodies = tagBodies(specs, client.listSchemes(workDate));
        if (!tagBodies.isEmpty()) {
            Path file = write("docSchemesTag", "SAVE_MENU_MAP", "TagDocScheme", "status", runId, new ArrayList<>(tagBodies));
            tagRun = run(file, tagBodies.size(), vus, tenant, lineHandler);
            if (tagRun.passed()) BulkDataFiles.deleteQuietly(file);
        }
        return new Result(createRun, tagRun, specs.size(), countReady(specs, client.listSchemes(workDate)));
    }

    /** The specs whose scheme is not in {@code listed} yet (by name, case-insensitive). */
    public static List<Spec> missingSchemes(List<Spec> specs, List<SchemeRef> listed) {
        return specs.stream().filter(s -> find(listed, s.docName()) == null).toList();
    }

    /**
     * One tag body per existing scheme that lacks a menu: its already-tagged menus plus the missing ones, so nothing existing is dropped (the same
     * rule as {@link DocSchemeProvisioner}).
     */
    public static List<List<Map<String, Object>>> tagBodies(List<Spec> specs, List<SchemeRef> listed) {
        List<List<Map<String, Object>>> out = new ArrayList<>();
        for (Spec spec : specs) {
            SchemeRef scheme = find(listed, spec.docName());
            if (scheme == null) continue;
            Set<String> have = new LinkedHashSet<>(scheme.menuShortcodes());
            if (have.containsAll(spec.menuShortcodes())) continue;
            Set<String> all = new LinkedHashSet<>(have);
            all.addAll(spec.menuShortcodes());
            List<Map<String, Object>> body = new ArrayList<>();
            for (Object entry : DocSchemeRequestFactory.tag(scheme.code(), new ArrayList<>(all))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = MAPPER.convertValue(entry, Map.class);
                body.add(map);
            }
            out.add(body);
        }
        return out;
    }

    /** How many specs have their scheme listed with every menu tagged. */
    public static int countReady(List<Spec> specs, List<SchemeRef> listed) {
        int n = 0;
        for (Spec spec : specs) {
            SchemeRef scheme = find(listed, spec.docName());
            if (scheme != null && scheme.menuShortcodes().containsAll(spec.menuShortcodes())) n++;
        }
        return n;
    }

    private static List<Object> createBodies(List<Spec> toCreate, AccountingPeriod period) {
        List<Object> bodies = new ArrayList<>(toCreate.size());
        for (Spec s : toCreate) {
            bodies.add(MAPPER.convertValue(DocSchemeRequestFactory.create(s.docName(), s.prefix(), s.width(), DocSchemeProvisioner.TEST_SCHEME_REMARKS,
                    period.fyySuffix(), period.dateFrom(), period.dateTo()), Map.class));
        }
        return bodies;
    }

    private static Path write(String kind, String endpoint, String logTag, String expect, String runId, List<?> bodies) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("logTag", logTag);
        file.put("endpointGroup", "DocScheme");
        file.put("endpoint", endpoint);
        file.put("runId", runId);
        file.put("expect", expect);
        file.put("bodies", bodies);
        return BulkDataFiles.write(kind, runId, file);
    }

    private static K6Result run(Path file, int count, int vus, String tenant, Consumer<String> lineHandler) {
        return K6Runner.configured().run(K6Scripts.resolve("bulkPost"), PartyBulkProvisioner.env(file, count, vus, tenant), lineHandler);
    }

    private static SchemeRef find(List<SchemeRef> schemes, String docName) {
        return schemes.stream().filter(s -> s.docName() != null && s.docName().equalsIgnoreCase(docName)).findFirst().orElse(null);
    }
}
