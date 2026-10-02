package com.gsl.api.model.transferout;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of {@code TransferOutCreateRequest.items} ({@code POST /SI/Save}) — a single line of
 * the Transfer Out, mirroring {@code D:\PerformanceAutomationTesting\flows\createTransferOut.js}'s
 * {@code items[]} entries. Built by {@code TransferOutApiClient.createTransferOut(String, String,
 * int, int)}, which threads its {@code dcCode} parameter straight into {@link #dcCode} without
 * first resolving it via {@code TransferOutApiClient.searchChallan(String)} (see that method's
 * own note) — the framework has no verified example of {@link #batchSerialCode} being populated.
 *
 * <p>{@code @JsonInclude(NON_NULL)}: {@link #rate} and {@link #batchSerialCode} are omitted from
 * the request body entirely when left {@code null}, rather than serialized as {@code null}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferOutLineItem {
    @Builder.Default
    private int code = 0;
    private String dcCode;
    private String itemCode;
    private int invQty;
    private Integer rate;
    private String batchSerialCode;
}
