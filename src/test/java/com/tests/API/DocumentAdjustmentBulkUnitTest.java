package com.tests.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.bulk.DocumentAdjustmentBulk;
import com.gsl.api.bulk.DocumentAdjustmentBulk.UntagTarget;
import com.gsl.api.client.DocumentAdjustmentApiClient;
import com.gsl.api.core.DataSetupException;
import com.gsl.api.model.common.SubLedgerLookupResult;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.expectThrows;

/** The Document Adjustment hand-over to k6: sub-ledger targets of the read scenario, the untag bodies, and the guard that nothing is untagged unnamed. */
public class DocumentAdjustmentBulkUnitTest {

    @Test
    public void theReadFileCarriesTheModuleTypeScopeAndTargets() throws Exception {
        String runId = "unit" + System.nanoTime();
        List<SubLedgerLookupResult> sls = List.of(SubLedgerLookupResult.builder().slCode(42).glCode(7).build(), SubLedgerLookupResult.builder().slCode(43).glCode(7).build());
        Path file = DocumentAdjustmentBulk.writeReadTargets(DocumentAdjustmentApiClient.MODULE_AR, 1, "2026-10-30T00:00:00", sls, runId);
        try {
            JsonNode root = new ObjectMapper().readTree(file.toFile());
            assertEquals(root.get("endpointPrefix").asText(), "AR");
            assertEquals(root.get("module").asText(), "arvoucher");
            assertEquals(root.get("docType").asText(), DocumentAdjustmentApiClient.BASE_DOCUMENT_TYPE_VOUCHER);
            assertEquals(root.get("targets").size(), 2);
            assertEquals(root.get("targets").get(1).get("slCode").asLong(), 43L);
            assertEquals(root.get("targets").get(1).get("glCode").asLong(), 7L);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void anUnknownModuleOrNoSubLedgersIsRefused() {
        expectThrows(IllegalArgumentException.class, () -> DocumentAdjustmentBulk.writeReadTargets("glvoucher", 1, "d",
                List.of(SubLedgerLookupResult.builder().slCode(1).glCode(1).build()), "r"));
        expectThrows(DataSetupException.class, () -> DocumentAdjustmentBulk.writeReadTargets("apvoucher", 1, "d", List.of(), "r"));
    }

    @Test
    public void untagBodiesKeepTheApplicationsPascalCaseKeysAndAnEmptyAdjustedList() {
        List<Object> bodies = DocumentAdjustmentBulk.untagBodies(List.of(new UntagTarget(551, "VP/00001/26-27", new BigDecimal("1200.50"), 42)), 513150, 1);
        Map<?, ?> b = (Map<?, ?>) bodies.get(0);
        assertEquals(b.get("BaseDocumentType"), "VDP");
        assertEquals(((Number) b.get("BaseDocumentCode")).longValue(), 551L);
        assertEquals(b.get("BaseDocumentNumber"), "VP/00001/26-27");
        assertEquals(((Number) b.get("BaseDocumentSLCode")).longValue(), 42L);
        assertEquals(((Number) b.get("ConnectedUserCode")).longValue(), 513150L);
        assertEquals(b.get("AdjustedDocuments"), List.of());
    }

    @Test
    public void nothingIsUntaggedUnlessNamed() {
        expectThrows(DataSetupException.class, () -> DocumentAdjustmentBulk.untag(List.of(), 1, 1, 1, "r", "qaprod", l -> { }));
    }

    @Test
    public void budgetsAreSetOnlyWhenGiven() {
        Map<String, String> env = DocumentAdjustmentBulk.readsEnv(Path.of("d.json"), "qaprod", 3, "30s", 800, null);
        assertEquals(env.get("DOCADJ_PENDING_P95_MS"), "800");
        assertFalse(env.containsKey("DOCADJ_REFERENCE_P95_MS"));
        assertEquals(env.get("TARGET_VUS"), "3");
    }
}