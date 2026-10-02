package com.gsl.api.model.transferout;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.util.List;

/**
 * {@code POST /SI/Save} request body (Transfer Out) — built and sent by
 * {@code TransferOutApiClient.createTransferOut(TransferOutCreateRequest)}. Field set mirrors
 * {@code D:\PerformanceAutomationTesting\flows\createTransferOut.js}'s final save payload.
 *
 * <p>k6's flow populates {@link #ownerSiteCode}, {@link #tradeGroupCode}, and
 * {@link #docSchemeCode} from real business data resolved earlier in its chain (site/trade-group/
 * doc-scheme lookups). {@code TransferOutApiClient}'s current
 * {@code createTransferOut(String, String, int, int)} overload — the one this framework's tests
 * actually call — leaves all three {@code null} and only sets {@link #invoiceDate} and
 * {@link #items}; {@code @JsonInclude(NON_NULL)} means they're simply omitted from the request
 * rather than sent as {@code null}. Whether the server requires them for every Transfer Out or
 * only for scenarios this framework hasn't exercised yet is unconfirmed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferOutCreateRequest {
    @Builder.Default
    private int code = 0;
    @Builder.Default
    private String entryMode = "Add";
    private String invoiceDate; // ISO date-time

    @Singular
    private List<TransferOutLineItem> items;

    /** See class Javadoc — not populated by this framework's current create overload. */
    private Integer ownerSiteCode;
    /** See class Javadoc — not populated by this framework's current create overload. */
    private Integer tradeGroupCode;
    /** See class Javadoc — not populated by this framework's current create overload. */
    private Integer docSchemeCode;
}
