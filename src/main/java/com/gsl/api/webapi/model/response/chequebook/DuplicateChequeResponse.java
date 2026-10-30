package com.gsl.api.webapi.model.response.chequebook;

import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Response of {@code POST /cheques/duplicatecheques}: {@code result} lists the numbers already used (empty = the range is free). */
@Getter
@Setter
public class DuplicateChequeResponse extends ApiEnvelopeBase {
    private List<Integer> result;

    /** The used numbers, never null. */
    public List<Integer> usedNumbers() {
        return result == null ? List.of() : result;
    }
}
