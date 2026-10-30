package com.gsl.api.webapi.model.response.voucher;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/** Response of {@code GET /vouchers/*vouchers/rootview}: {@code result} is the voucher; every field is kept so tests can read any header value. */
@Getter
@Setter
public class VoucherRootviewResponse extends ApiEnvelopeBase {
    private Voucher result;

    /** The voucher's {@code documentStatus} ("P" posted, "U" unposted), or null. */
    public String documentStatus() {
        return result == null ? null : result.getDocumentStatus();
    }

    /** All header fields as the API reports them; empty when there is no result. */
    public Map<String, Object> header() {
        return result == null ? Map.of() : result.fields();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class Voucher {
        private String documentStatus;
        private final Map<String, Object> other = new LinkedHashMap<>();

        @JsonAnySetter
        public void put(String key, Object value) {
            other.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> other() {
            return other;
        }

        /** Every field including {@code documentStatus}. */
        public Map<String, Object> fields() {
            Map<String, Object> all = new LinkedHashMap<>(other);
            if (documentStatus != null) all.put("documentStatus", documentStatus);
            return all;
        }
    }
}
