package com.gsl.model.finance;

import lombok.Data;

/**
 * Maps to finnar — GL-scoped master list of registered narration texts.
 * Not a per-voucher table: rows are keyed by (glcode, nartext), no FK to finvchmain.
 */
@Data
public class FinNar {

    private long narcode;
    private String nartext;
    private long glcode;
}
