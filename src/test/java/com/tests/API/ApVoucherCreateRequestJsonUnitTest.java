package com.tests.API;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsl.api.model.apvoucher.ApVoucherCreateRequest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * The AP Voucher create body must carry the HAR's exact flag names (APSave HAR: {@code "isAutoReleaseApplied"}, {@code "isReadOnlyInstance"},
 * {@code "IsMarkedForSaveAndRelease"}). With Lombok, a boolean field called {@code isX} serializes as {@code x} unless the name is pinned, so a
 * flag set here would silently never reach the server.
 */
public class ApVoucherCreateRequestJsonUnitTest {

    private static JsonNode json(ApVoucherCreateRequest request) throws Exception {
        return new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(request));
    }

    @Test
    public void autoReleaseIsSentUnderTheApplicationsFieldName() throws Exception {
        ApVoucherCreateRequest request = ApVoucherCreateRequest.builder().build();
        request.setAutoReleaseApplied(true);

        JsonNode body = json(request);

        assertTrue(body.has("isAutoReleaseApplied"), body.toString());
        assertTrue(body.get("isAutoReleaseApplied").asBoolean(), body.toString());
        assertFalse(body.has("autoReleaseApplied"), "the un-prefixed name is not a field the server knows: " + body);
    }

    @Test
    public void readOnlyInstanceAndSaveAndReleaseKeepTheirNames() throws Exception {
        JsonNode body = json(ApVoucherCreateRequest.builder().build());
        assertTrue(body.has("isReadOnlyInstance"), body.toString());
        assertTrue(body.has("IsMarkedForSaveAndRelease"), body.toString());
        assertFalse(body.get("isAutoReleaseApplied").asBoolean(), "default stays false so nothing else changes");
    }
}
