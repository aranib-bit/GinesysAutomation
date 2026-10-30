package com.tests.API;

import com.gsl.api.bulk.VoucherBulkProvisioner;
import com.gsl.api.bulk.VoucherBulkProvisioner.Action;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** The voucher lifecycle hand-over to k6: one body per voucher in the application's own shape, and what "now in the expected state" means. */
public class VoucherBulkProvisionerUnitTest {

    @Test
    public void revertAndReleaseSendOneVoucherPerBodyInTheActionShape() {
        List<Object> bodies = VoucherBulkProvisioner.bodies(Action.REVERT, List.of(11L, 12L), 513150L, 1L);
        assertEquals(bodies.size(), 2);
        Map<?, ?> first = (Map<?, ?>) bodies.get(0);
        assertEquals(first.get("code"), List.of(11L));
        assertEquals(((Number) first.get("empCode")).longValue(), 513150L);
        assertEquals(((Number) first.get("connectedOUCode")).longValue(), 1L);
        assertFalse(first.containsKey("voucherCodes"));
    }

    @Test
    public void deleteUsesTheVoucherCodesShape() {
        Map<?, ?> body = (Map<?, ?>) VoucherBulkProvisioner.bodies(Action.DELETE, List.of(7L), 1L, 1L).get(0);
        assertEquals(body.get("voucherCodes"), List.of(7L));
        assertTrue(body.containsKey("empCode") && body.containsKey("connectedOUCode"), body.keySet().toString());
    }

    @Test
    public void theStateCheckCountsOnlyVouchersInTheExpectedState() {
        List<Long> codes = List.of(1L, 2L, 3L);
        Map<Long, String> status = Map.of(1L, "U", 2L, "P", 3L, "U");
        assertEquals(VoucherBulkProvisioner.countInState(codes, status::get, Action.REVERT), 2);
        assertEquals(VoucherBulkProvisioner.countInState(codes, status::get, Action.RELEASE), 1);
        assertEquals(VoucherBulkProvisioner.countInState(codes, c -> null, Action.DELETE), 3, "a deleted voucher no longer exists");
        assertEquals(VoucherBulkProvisioner.countInState(codes, status::get, Action.DELETE), 0);
    }
}