package com.gsl.api.webapi.model.request.chequebook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of {@code POST /cheques/duplicatecheques}: which numbers of {@code [start, end]} are already used for the bank GL. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateChequeRequest {
    private int chequeStartNumber;
    private int chequeEndNumber;
    private int chequeWidth;
    private long glCode;

    /** Reads in the order the callers think of it: GL, first number, last number, zero-padding width. */
    public static DuplicateChequeRequest of(long glCode, int start, int end, int width) {
        return new DuplicateChequeRequest(start, end, width, glCode);
    }
}
