package com.gsl.api.webapi.model.response.common;

import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

/** An envelope whose {@code result} is a plain string (a server-issued code, or a validation message that is null when there is none). */
@Getter
@Setter
public class StringResultResponse extends ApiEnvelopeBase {
    private String result;
}
