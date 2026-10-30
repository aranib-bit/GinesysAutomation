package com.tests.API;

import com.gsl.api.bulk.K6Result;
import com.gsl.api.bulk.K6Status;
import com.gsl.api.bulk.PartyBulkDataFile;
import com.gsl.api.bulk.PartyBulkProvisioner;
import com.gsl.api.webapi.model.response.party.PartyListItem;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** The hand-over between Java and the k6 party scenario: the environment k6 reads, finding a run again in the party list, and what "complete" means. */
public class PartyBulkProvisionerUnitTest {

    private static PartyListItem party(String slId) {
        PartyListItem p = new PartyListItem();
        p.setSlId(slId);
        return p;
    }

    private static K6Result k6(int exit) {
        return new K6Result(exit, K6Status.fromExitCode(exit), 3, 0, List.of());
    }

    @Test
    public void onlyThePartiesOfThisRunAreCounted() {
        String prefix = PartyBulkDataFile.slIdPrefix("20261030101500");
        List<PartyListItem> listed = List.of(party(prefix + "0"), party(prefix + "1"), party("ZB20261030999999" + "0"), party("C000001"), party(null));
        assertEquals(PartyBulkProvisioner.countRun(listed, "20261030101500"), 2);
    }

    @Test
    public void extinctCountingLooksOnlyAtThisRunsPartiesInTheRequestedState() {
        String prefix = PartyBulkDataFile.slIdPrefix("r1");
        PartyListItem extinct = party(prefix + "0");
        extinct.setExtinct("Yes");
        PartyListItem active = party(prefix + "1");
        active.setExtinct("No");
        PartyListItem foreignExtinct = party("C000001");
        foreignExtinct.setExtinct("Yes");
        List<PartyListItem> listed = List.of(extinct, active, foreignExtinct);
        assertEquals(PartyBulkProvisioner.countRunInState(listed, "r1", true), 1);
        assertEquals(PartyBulkProvisioner.countRunInState(listed, "r1", false), 1);
    }

    @Test
    public void anOthersBodyKeepsTheWireNamesOfTheCapturedRequest() {
        Map<?, ?> body = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(
                com.gsl.api.model.subledgerothers.SubLedgerOthersCreateRequest.builder().slname("n").slid("s").clscode(6).glcode(2).build(), Map.class);
        for (String key : List.of("slname", "slid", "clscode", "glcode", "finSLGLs", "udfDetails", "bfX1", "bpH1")) {
            assertTrue(body.containsKey(key), key + " missing in " + body.keySet());
        }
    }

    @Test
    public void theScenarioEnvironmentCarriesTheDataFileCountAndTenant() {
        Map<String, String> env = PartyBulkProvisioner.env(Path.of("data", "parties-r1.json"), 20, 4, "qaprod");
        assertEquals(env.get("TARGET_DOCS"), "20");
        assertEquals(env.get("TARGET_VUS"), "4");
        assertEquals(env.get("TENANT"), "qaprod");
        assertTrue(Path.of(env.get("BULK_DATA_FILE")).isAbsolute(), env.get("BULK_DATA_FILE"));
    }

    @Test
    public void zeroVusLeavesTheScenarioDefault() {
        assertFalse(PartyBulkProvisioner.env(Path.of("p.json"), 1, 0, "qaprod").containsKey("TARGET_VUS"));
    }

    @Test
    public void aRunIsCompleteOnlyWhenK6PassedAndEveryPartyIsInTheMaster() {
        assertTrue(new PartyBulkProvisioner.Result(k6(0), 3, 3).complete());
        assertFalse(new PartyBulkProvisioner.Result(k6(0), 3, 2).complete(), "k6 said fine but a party is missing from the master");
        assertFalse(new PartyBulkProvisioner.Result(k6(99), 3, 3).complete(), "a crossed threshold is not a clean run");
    }
}

