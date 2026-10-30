package com.tests.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.bulk.BulkDataFiles;
import com.gsl.api.bulk.FinanceBulkDataFile;
import com.gsl.api.service.ChequeBookService;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** The cheque-book data file k6 walks: candidate ranges per book, never shared between books, with ready-made bodies of the wire shape. */
public class FinanceBulkDataFileUnitTest {

    @Test
    public void everyBookGetsItsOwnNonOverlappingCandidateRanges() throws Exception {
        ChequeBookService.Settings settings = new ChequeBookService.Settings(5, 6, 100, 3);
        String runId = "unit" + System.nanoTime();
        Path file = FinanceBulkDataFile.writeChequeBooks(77L, 3L, 9L, "2026-10-30T00:00:00", 4, runId, settings);
        try {
            JsonNode root = new ObjectMapper().readTree(file.toFile());
            assertEquals(root.get("endpoint").asText(), "CREATE");
            assertEquals(root.get("endpointGroup").asText(), "Cheque");
            assertEquals(root.get("books").size(), 4);

            Set<Integer> starts = new HashSet<>();
            for (JsonNode book : root.get("books")) {
                assertEquals(book.get("attempts").size(), 3);
                for (JsonNode attempt : book.get("attempts")) {
                    assertTrue(starts.add(attempt.get("start").asInt()), "range start used twice: " + attempt.get("start"));
                    JsonNode dup = attempt.get("duplicateCheck");
                    assertEquals(dup.get("glCode").asLong(), 77L);
                    assertEquals(dup.get("chequeEndNumber").asInt() - dup.get("chequeStartNumber").asInt(), 4);
                    assertEquals(attempt.get("create").get("chequeDetails").size(), 5);
                }
            }
            assertEquals(starts.size(), 12);
            assertEquals(starts.stream().min(Integer::compare).orElseThrow().intValue(), 100);
            assertEquals(starts.stream().max(Integer::compare).orElseThrow().intValue(), 100 + 11 * 5);
            assertEquals(BulkDataFiles.count(file, "books"), 4);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
