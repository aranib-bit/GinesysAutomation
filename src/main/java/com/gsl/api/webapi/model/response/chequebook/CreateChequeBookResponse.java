package com.gsl.api.webapi.model.response.chequebook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code POST /cheques/create}: {@code result.item2} is the new book's number. */
@Getter
@Setter
public class CreateChequeBookResponse extends ApiEnvelopeBase {
    private Result result;

    /** The new book number, or null when the response carried none. */
    public String bookNumber() {
        return result == null ? null : result.getBookNumber();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("item1")
        private List<Object> conflicts;

        @JsonProperty("item2")
        private String bookNumber;
    }
}
