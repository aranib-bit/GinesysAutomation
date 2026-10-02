package com.gsl.testdata.Finance.Operations.Vouchers.Common;

import com.gsl.enums.AdjustmentType;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/**
 * One adjustment to apply inside Adjustment Entry — which of the three methods to use
 * ({@link AdjustmentType}), plus that method's inputs. Shared by AR/AP Create's initial
 * adjustment, Edit's restore/change, and the "I will adjust more" additions, so one
 * implementation ({@code AdjustmentFlowSteps#applyAdjustment}) serves all three.
 */
@Value
@Builder
public class AdjustmentSpec {

    AdjustmentType type;

    /** SELECT_DOCUMENT: documents to tick, each with an optional per-document amount override
     *  ({@code null}/zero amount keeps the UI's default). */
    @Singular("doc")
    List<DocAmount> docs;

    /** REFERENCE_SITEWISE_FIFO_SETOFF: reference-site names to add as rows before auto-adjust. */
    @Singular("referenceSite")
    List<String> referenceSites;

    @Value
    public static class DocAmount {
        String documentNumber;
        BigDecimal amount;
    }

    public static AdjustmentSpec selectDocument(String documentNumber, BigDecimal amount) {
        return AdjustmentSpec.builder().type(AdjustmentType.SELECT_DOCUMENT)
                .doc(new DocAmount(documentNumber, amount)).build();
    }

    public static AdjustmentSpec fifo() {
        return AdjustmentSpec.builder().type(AdjustmentType.FIFO_SETOFF).build();
    }
}
