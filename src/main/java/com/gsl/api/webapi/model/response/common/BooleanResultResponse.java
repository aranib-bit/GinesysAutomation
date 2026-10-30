package com.gsl.api.webapi.model.response.common;

import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

/** An envelope whose {@code result} is a plain boolean (create/update acknowledgements, tag checks). */
@Getter
@Setter
public class BooleanResultResponse extends ApiEnvelopeBase {
    private Boolean result;

    public boolean isTrue() {
        return Boolean.TRUE.equals(result);
    }
}
