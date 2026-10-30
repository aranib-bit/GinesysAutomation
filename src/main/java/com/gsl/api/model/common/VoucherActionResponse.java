package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Response of the voucher actions {@code PUT /vouchers/{ap|ar|general}/revert}, {@code .../release} and {@code DELETE /vouchers/{...}}: one
 * result row per voucher ({@code item1} vchcode, {@code item2} number, {@code item3} "success" or the application's refusal message,
 * {@code item4} post code). The HTTP/{@code success} flag is true even when the application refuses a voucher (e.g. "Adjustment(s) made
 * against this record. Revert posting not allowed."), so callers that must know the voucher really changed state use {@link #allSucceeded()}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VoucherActionResponse {
    private int status;
    private String message;
    private boolean success;
    private String error;
    private List<Item> result;

    /** True when the call succeeded AND every voucher reported "success" (case-insensitive). */
    public boolean allSucceeded() {
        return success && result != null && !result.isEmpty() && result.stream().allMatch(Item::succeeded);
    }

    /** The non-success {@code item3} messages, in order; empty when every voucher succeeded. */
    public List<String> failureMessages() {
        if (result == null) return List.of();
        return result.stream().filter(i -> !i.succeeded()).map(Item::getItem3).collect(Collectors.toList());
    }

    /** The first non-success message, or null when there is none. */
    public String firstFailureMessage() {
        List<String> messages = failureMessages();
        return messages.isEmpty() ? error : messages.get(0);
    }

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
