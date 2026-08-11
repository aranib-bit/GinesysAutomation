package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import lombok.Getter;
import lombok.Setter;

/**
 * Holds optional/post-load fields that must not be scanned by XlsxReader's field
 * mapper. XlsxReader calls getDeclaredFields() on the concrete target class only —
 * inherited fields are invisible to it, so these fields never appear as required
 * Excel columns.
 *
 * <p>Mirrors {@code APVoucher.DocumentInformationBase}'s idiom (and
 * {@code GeneralVoucher.DocumentInformationBase}'s for {@code ownersite}):
 * declaring an unannotated field directly on the leaf {@code @Sheet} class does
 * NOT exclude it from XlsxReader's scan — only living in a superclass does.
 */
public abstract class ExpenseIncomeDetailBase {

    /** Optional soft-assertion column — populated post-load via raw POI. See {@link ExpenseIncomeDetail}. */
    @Getter
    @Setter
    private String expectedGlType;

    /** DB-resolved, authoritative GL type — populated post-load by the data source. See {@link ExpenseIncomeDetail}. */
    @Getter
    @Setter
    private GlLineType resolvedGlType;
}
