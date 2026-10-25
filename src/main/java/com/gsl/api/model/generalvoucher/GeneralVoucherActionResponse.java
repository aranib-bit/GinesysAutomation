package com.gsl.api.model.generalvoucher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Response of the General Voucher revert / release / delete calls — confirmed via HAR. The HTTP status is 200 and
 * {@code success=true} <em>even when a voucher cannot be processed</em>; the per-document outcome is {@code result[].item3}:
 * {@code "success"} (revert/release) or {@code "Success"} (delete) on success, any other text is the failure message — e.g.
 * {@code "BRS is done for this transaction. Cannot be reverted."}. Compare case-insensitively.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeneralVoucherActionResponse {

    private int status;
    private String message;
    private boolean success;
    private String error;
    private List<Item> result;

    /** True when the call succeeded and every returned document reports {@code item3 = success}. */
    public boolean allSucceeded() {
        return success && result != null && !result.isEmpty() && result.stream().allMatch(Item::succeeded);
    }

    /** The failure messages ({@code item3}) of the documents that did not succeed; empty when all did. */
    public List<String> failureMessages() {
        if (result == null) return List.of();
        return result.stream().filter(i -> !i.succeeded()).map(Item::getItem3).collect(Collectors.toList());
    }

    /** One {@code result[]} tuple: {@code item1}=vchcode, {@code item2}=document number, {@code item3}=outcome text, {@code item4}=dataVersion (absent for delete). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private long item1;
        private String item2;
        private String item3;
        private Long item4;

        public boolean succeeded() {
            return item3 != null && item3.trim().equalsIgnoreCase("success");
        }
    }
}
