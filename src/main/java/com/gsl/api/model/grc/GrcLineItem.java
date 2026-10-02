package com.gsl.api.model.grc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One line item inside {@link GrcCreateRequest#getItems()} — the WebAPI wire shape for GRC/Save's
 * {@code items[]} array (a reduced field set relative to flows/createGrc.js's full per-line
 * payload; only what {@code GrcApiClient.createGrc(String, int, int, int, int, int)} needs to
 * build a valid single-line GRC). {@code acqty}/{@code acrate} are the accepted quantity/rate;
 * {@code grsamt}/{@code netamt} are the gross/net amount (quantity &times; rate at this reduced
 * level, with no charge calculation applied); {@code batchSerialCode} is only populated for
 * Batch/Serial items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrcLineItem {
    private String itemId;
    private int acqty;
    private int acrate;
    private Integer grsamt;
    private Integer netamt;
    private String batchSerialCode;
    private Integer mrp;
    private Integer wsp;
}
