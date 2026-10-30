package com.tests.API;

import com.gsl.api.common.PartyClassRef;
import com.gsl.api.common.PartyKind;
import com.gsl.api.core.ApiException;
import com.gsl.api.webapi.client.PartyMasterWebApiClient;
import com.gsl.enums.ApiPlatform;
import com.gsl.service.party.PartyContext;
import com.gsl.service.party.PartyMasterGateway;
import com.gsl.service.party.PartyMasterService;
import com.tests.API.support.StubApiServer;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/** The party find-or-create workflow with fake gateways (platform fallback rules), and the typed party list against a stub server. */
public class PartyMasterServiceUnitTest {

    private static final PartyContext CTX = new PartyContext(1, 1, "2026-10-29T00:00:00");
    private static final PartyClassRef JOBBER = new PartyClassRef(4, "Jobber", 'J');
    private static final PartyClassRef CUSTOMER = new PartyClassRef(1, "Customer", 'C');

    /** Fake platform: scripted classes, one scripted create outcome, and the ids it was asked to create. */
    private static final class FakeGateway implements PartyMasterGateway {
        final ApiPlatform platform;
        final List<PartyClassRef> classes;
        RuntimeException createFailure;
        final List<String> created = new ArrayList<>();

        FakeGateway(ApiPlatform platform, List<PartyClassRef> classes) {
            this.platform = platform;
            this.classes = classes;
        }

        @Override
        public ApiPlatform platform() {
            return platform;
        }

        @Override
        public List<PartyClassRef> classes(PartyContext c) {
            return classes;
        }

        @Override
        public void create(PartyClassRef cls, String name, String slId, PartyContext c) {
            if (createFailure != null) throw createFailure;
            created.add(slId);
        }

        @Override
        public boolean exists(PartyKind.Family family, String slId, PartyContext c) {
            return created.contains(slId);
        }
    }

    private static PartyMasterService service(PartyMasterGateway... gateways) {
        return new PartyMasterService(List.of(gateways), 3_000L);
    }

    @Test
    public void nothingIsCreatedWhenThePartyIsAlreadyOffered() {
        FakeGateway g = new FakeGateway(ApiPlatform.WEBAPI, List.of(JOBBER));
        service(g).ensurePartyOfClass(4, CTX, () -> true);
        assertTrue(g.created.isEmpty());
    }

    @Test
    public void aMissingPartyIsCreatedAndTheServiceWaitsUntilItIsOffered() {
        FakeGateway g = new FakeGateway(ApiPlatform.WEBAPI, List.of(JOBBER, CUSTOMER));
        AtomicBoolean visible = new AtomicBoolean();
        service(g).ensurePartyOfClass(4, CTX, () -> {
            if (!g.created.isEmpty()) visible.set(true);
            return visible.get();
        });
        assertEquals(g.created.size(), 1);
        assertTrue(g.created.get(0).startsWith("ZP"));
    }

    @Test
    public void aTransportFailureFallsThroughToTheNextPlatform() {
        FakeGateway first = new FakeGateway(ApiPlatform.WEBAPI, List.of(JOBBER));
        first.createFailure = new ApiException("connection reset", 0);
        FakeGateway second = new FakeGateway(ApiPlatform.PUBLICAPI, List.of(JOBBER));
        service(first, second).ensurePartyOfClass(4, CTX, () -> !second.created.isEmpty());
        assertTrue(first.created.isEmpty());
        assertEquals(second.created.size(), 1);
    }

    @Test
    public void anUnsupportedOperationFallsThroughToo() {
        FakeGateway first = new FakeGateway(ApiPlatform.PUBLICAPI, List.of(JOBBER));
        first.createFailure = new UnsupportedOperationException("not implemented");
        FakeGateway second = new FakeGateway(ApiPlatform.WEBAPI, List.of(JOBBER));
        service(first, second).ensurePartyOfClass(4, CTX, () -> !second.created.isEmpty());
        assertEquals(second.created.size(), 1);
    }

    @Test
    public void aBusinessErrorIsNotRetriedOnAnotherPlatform() {
        FakeGateway first = new FakeGateway(ApiPlatform.WEBAPI, List.of(JOBBER));
        first.createFailure = new ApiException("Customer Id cannot be duplicate", 400);
        FakeGateway second = new FakeGateway(ApiPlatform.PUBLICAPI, List.of(JOBBER));
        expectThrows(ApiException.class, () -> service(first, second).ensurePartyOfClass(4, CTX, () -> false));
        assertTrue(second.created.isEmpty());
    }

    @Test
    public void aClassNoPlatformKnowsIsASkip() {
        FakeGateway g = new FakeGateway(ApiPlatform.WEBAPI, List.of(CUSTOMER));
        SkipException e = expectThrows(SkipException.class, () -> service(g).ensurePartyOfClass(99, CTX, () -> false));
        assertTrue(e.getMessage().contains("class 99"), e.getMessage());
    }

    @Test
    public void aPartyThatNeverBecomesOfferedFails() {
        FakeGateway g = new FakeGateway(ApiPlatform.WEBAPI, List.of(JOBBER));
        ApiException e = expectThrows(ApiException.class, () -> new PartyMasterService(List.of(g), 1_000L).ensurePartyOfClass(4, CTX, () -> false));
        assertTrue(e.getMessage().contains("not offered"), e.getMessage());
    }

    @Test
    public void thePartyListIsTypedAndPaged() {
        try (StubApiServer server = StubApiServer.start()) {
            server.respond("POST", "/VendorMaster/GetVendors", 200,
                    "{\"success\":true,\"result\":{\"totalCount\":2,\"items\":["
                            + "{\"code\":352,\"slid\":\"J11111\",\"slname\":\"Jobber\",\"slclass\":\"Jobber\",\"slclasstype\":\"Jobber\",\"isextinct\":\"No\",\"tdsapplicable\":\"No\",\"extra\":1},"
                            + "{\"code\":353,\"slid\":\"S1\",\"slname\":\"Sup\",\"slclass\":\"Supplier\",\"isextinct\":\"Yes\",\"tdsapplicable\":\"Yes\"}]}}");
            var items = new PartyMasterWebApiClient(server.spec()).list(PartyKind.Family.VENDOR_MASTER, 1, 1, "2026-10-29T00:00:00");
            assertEquals(items.size(), 2);
            assertEquals(items.get(0).getSlId(), "J11111");
            assertEquals(items.get(0).getCode().longValue(), 352L);
            assertTrue(!items.get(0).isExtinct() && !items.get(0).isTdsApplicable());
            assertTrue(items.get(1).isExtinct() && items.get(1).isTdsApplicable());
            assertEquals(server.seen().size(), 1, "totalCount reached on the first page: one request");
            assertTrue(server.last().body().contains("\"connectedSiteOUCode\":1"), server.last().body());
            assertTrue(server.last().body().contains("\"limit\":100"), server.last().body());
        }
    }
}
