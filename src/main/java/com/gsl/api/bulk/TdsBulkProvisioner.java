package com.gsl.api.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.client.TdsMasterApiClient;
import com.gsl.service.TdsMasterService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Bulk TDS masters through k6. A section depends only on a source and an agency, so Java makes sure those exist (reusing what is there, creating
 * a throw-away one only when the environment has none), builds every body, and k6 posts them; Java then reads the section/source list back and
 * counts the run's records. The TDS creates answer HTTP 201 without the usual envelope, so the data file tells k6 which statuses mean success.
 */
public final class TdsBulkProvisioner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TdsBulkProvisioner() {}

    /** @param type {@code 'S'} Services, {@code 'G'} Goods, or null to alternate */
    public static PartyBulkProvisioner.Result createSections(int count, int vus, String type, String runId, TdsMasterApiClient client,
                                                             TdsMasterApiClient.Context ctx, String tenant, Consumer<String> lineHandler) {
        TdsMasterService service = new TdsMasterService(client, ctx);
        Path file = write("tdsSections", "SECTION_CREATE", "CreateTdsSection", runId, service.buildSectionRequests(count, runId, type));
        K6Result k6 = run(file, count, vus, tenant, lineHandler);
        String prefix = TdsMasterService.sectionNamePrefix(runId);
        int found = (int) client.listSections(ctx).stream().filter(s -> s.getTdsName() != null && s.getTdsName().startsWith(prefix)).count();
        return finish(file, new PartyBulkProvisioner.Result(k6, count, found));
    }

    public static PartyBulkProvisioner.Result createSources(int count, int vus, String runId, TdsMasterApiClient client,
                                                            TdsMasterApiClient.Context ctx, String tenant, Consumer<String> lineHandler) {
        TdsMasterService service = new TdsMasterService(client, ctx);
        Path file = write("tdsSources", "SOURCE_CREATE", "CreateTdsSource", runId, service.buildSourceRequests(count, runId));
        K6Result k6 = run(file, count, vus, tenant, lineHandler);
        String prefix = TdsMasterService.sourceNamePrefix(runId);
        int found = (int) client.listSources(ctx).stream().filter(s -> s.getName() != null && s.getName().startsWith(prefix)).count();
        return finish(file, new PartyBulkProvisioner.Result(k6, count, found));
    }

    private static Path write(String kind, String endpoint, String logTag, String runId, List<?> requests) {
        List<Object> bodies = new ArrayList<>(requests.size());
        for (Object r : requests) bodies.add(MAPPER.convertValue(r, Map.class));
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("logTag", logTag);
        file.put("endpointGroup", "Tds");
        file.put("endpoint", endpoint);
        file.put("runId", runId);
        // TDS creates answer 201 with the created name and no {success,result} envelope.
        file.put("okStatuses", List.of(200, 201));
        file.put("expect", "status");
        file.put("bodies", bodies);
        return BulkDataFiles.write(kind, runId, file);
    }

    private static K6Result run(Path file, int count, int vus, String tenant, Consumer<String> lineHandler) {
        return K6Runner.configured().run(K6Scripts.resolve("bulkPost"), PartyBulkProvisioner.env(file, count, vus, tenant), lineHandler);
    }

    private static PartyBulkProvisioner.Result finish(Path file, PartyBulkProvisioner.Result result) {
        if (result.complete()) BulkDataFiles.deleteQuietly(file);
        return result;
    }
}
