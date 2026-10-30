package com.gsl.service;

import com.gsl.api.client.TransferOutApiClient;
import com.gsl.api.model.transferout.TransferOutCreateRequest;
import com.gsl.api.model.transferout.TransferOutCreateResponse;
import com.gsl.api.model.transferout.TransferOutLineItem;
import com.gsl.api.webapi.factory.ServiceInvoiceRequestFactory;
import com.gsl.reports.StepExecutor;

import java.time.LocalDate;

/** Transfer Out workflows: builds and saves a single-line Transfer Out against a DC (the client only makes the HTTP calls). */
public class TransferOutService {

    private final TransferOutApiClient client;

    public TransferOutService(TransferOutApiClient client) {
        this.client = client;
    }

    /** Builds and saves a single-line Transfer Out against the given DC, matching k6's flow. */
    public TransferOutCreateResponse createTransferOut(String dcCode, String itemCode, int quantity, int rate) {
        // * NOTE: this overload does not call searchChallan() — dcCode is threaded straight into
        // the line item as supplied by the caller. Only the actual Save call below gets its own
        // sub-section; a "search" node here would misrepresent a call that isn't made.
        TransferOutLineItem line = TransferOutLineItem.builder()
                .dcCode(dcCode)
                .itemCode(itemCode)
                .invQty(quantity)
                .rate(rate)
                .build();

        TransferOutCreateRequest request = TransferOutCreateRequest.builder()
                .invoiceDate(ServiceInvoiceRequestFactory.stamp(LocalDate.now()))
                .item(line)
                .build();

        return StepExecutor.call("Create Transfer Out via API", () -> client.createTransferOut(request));
    }
}
