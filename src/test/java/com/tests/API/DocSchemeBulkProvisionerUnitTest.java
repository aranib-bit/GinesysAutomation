package com.tests.API;

import com.gsl.api.bulk.DocSchemeBulkProvisioner;
import com.gsl.api.client.DocSchemeAdminApiClient.SchemeRef;
import com.gsl.api.integration.DocSchemeProvisioner.Spec;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** Which doc schemes still have to be created or menu-tagged, and that a real scheme is refused before anything is posted. */
public class DocSchemeBulkProvisionerUnitTest {

    private static final Spec A = new Spec("ZZ TEST A", "ZA", 3, List.of("FinanceARVoucher", "FinanceAPVoucher"));
    private static final Spec B = new Spec("ZZ TEST B", "ZB", 4, List.of("FinanceGenVoucher"));

    @Test
    public void onlySchemesNotListedYetAreCreated() {
        List<SchemeRef> listed = List.of(new SchemeRef(10, "zz test a", List.of()));
        assertEquals(DocSchemeBulkProvisioner.missingSchemes(List.of(A, B), listed), List.of(B));
    }

    @Test
    public void taggingKeepsWhatIsAlreadyTaggedAndAddsOnlyTheMissingMenus() {
        List<SchemeRef> listed = List.of(new SchemeRef(10, "ZZ TEST A", List.of("FinanceARVoucher", "FinanceOther")), new SchemeRef(11, "ZZ TEST B", List.of("FinanceGenVoucher")));
        List<List<Map<String, Object>>> bodies = DocSchemeBulkProvisioner.tagBodies(List.of(A, B), listed);
        assertEquals(bodies.size(), 1, "B is already tagged, only A needs a call");
        assertEquals(bodies.get(0).size(), 3, "AR + the other existing menu + the missing AP");
        assertTrue(bodies.get(0).stream().allMatch(m -> ((Number) m.get("docCode")).longValue() == 10L), bodies.toString());
    }

    @Test
    public void readyMeansListedWithEveryMenuTagged() {
        List<SchemeRef> listed = List.of(new SchemeRef(10, "ZZ TEST A", List.of("FinanceARVoucher")), new SchemeRef(11, "ZZ TEST B", List.of("FinanceGenVoucher")));
        assertEquals(DocSchemeBulkProvisioner.countReady(List.of(A, B), listed), 1);
    }

    @Test
    public void aRealSchemeIsRefusedBeforeAnyCall() {
        Spec real = new Spec("AR/AP Voucher", "AP", 6, List.of("FinanceARVoucher"));
        expectThrows(IllegalStateException.class, () -> DocSchemeBulkProvisioner.ensureCreatedAndMenuTagged(List.of(real), 1, "r1", null, "qaprod", l -> { }));
    }
}