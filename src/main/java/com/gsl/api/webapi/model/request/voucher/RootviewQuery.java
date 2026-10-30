package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code GET /vouchers/{ap|ar|general}vouchers/rootview}: one voucher by its code. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RootviewQuery {
    @JsonProperty("code")
    private long code;
    @JsonProperty("connectedOUCode")
    private long connectedOuCode;
    @JsonProperty("connectedUserCode")
    private long connectedUserCode;
}
