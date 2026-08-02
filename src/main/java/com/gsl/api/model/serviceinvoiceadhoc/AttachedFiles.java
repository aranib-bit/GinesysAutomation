package com.gsl.api.model.serviceinvoiceadhoc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** ServiceInvoice/Save's {@code attachedFiles} field — always this fixed empty shape for a new invoice. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachedFiles {
    @Builder.Default
    private int timestamp = 0;
    @Builder.Default
    private String docType = "";
    @Builder.Default
    private int docCode = 0;
    @Builder.Default
    private List<Object> files = List.of();
}
