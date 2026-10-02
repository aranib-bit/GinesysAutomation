package com.gsl.api.model.transferout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of {@code POST /SI/SearchChallan}'s {@code result} array — a DC eligible for
 * Transfer Out (Service Invoice against a challan). Returned by
 * {@code TransferOutApiClient.searchChallan(String)}; {@code TransferOutApiScenarioType}'s
 * {@code SEARCH_CHALLAN_*} scenarios exercise it directly, but the current
 * {@code TransferOutApiClient.createTransferOut(String, String, int, int)} overload does not call
 * {@code searchChallan} first — it threads a caller-supplied {@code dcCode} straight into
 * {@link TransferOutLineItem} instead (see that method's own note).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DcSearchResult {
    private String dcCode;
    private String packetBarcode;
}
