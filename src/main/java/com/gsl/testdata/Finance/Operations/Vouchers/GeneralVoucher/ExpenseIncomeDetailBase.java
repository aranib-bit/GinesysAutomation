package com.gsl.testdata.Finance.Operations.Vouchers.GeneralVoucher;

import com.gsl.enums.Finance.Operations.GeneralVoucher.GlLineType;
import com.gsl.enums.GlAccountType;
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

    /** DB-resolved GL account class (Asset/Liability/Income/Expense, {@code fingl.type}) —
     *  populated post-load by the data source, same idiom as {@link #resolvedGlType}. Plan §4A:
     *  needed to report a detail-line edit's GL change in terms a reader recognizes (not just
     *  "GL changed from X to Y" but "from an Expense GL to an Asset GL"). Null until a GL-type
     *  transition scenario (or 4A's reporting) sets it — every other scenario leaves it unset. */
    @Getter
    @Setter
    private GlAccountType resolvedAccountClass;
}
