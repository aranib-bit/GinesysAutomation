package com.gsl.validation.ui.voucher;

import com.gsl.enums.VoucherDomain;
import com.gsl.pages.enums.Finance.Operations.Common.FormSection;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherDocumentInformationBase;
import com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherFooterBase;
import com.gsl.utils.ui.UiFieldState;
import com.gsl.validation.ui.UiValidationFailure;
import com.gsl.validation.ui.UiValidationLevel;

import java.util.List;

/**
 * What one voucher looks like, as DATA for {@link VoucherUiValidationStage}. A spec holds no validation
 * logic and decides no ordering — the stage does both, once, for every voucher. A new voucher = one spec
 * class (plus its sheet in the UiStyleBaseline workbook); a new validation = one change in the stage.
 */
public interface VoucherUiSpec {

    VoucherDomain domain();

    VoucherDocumentInformationBase documentInformation();

    VoucherFooterBase footer();

    /** The collapsible grid section: {@code SUB_LEDGER_INFORMATION} (AR/AP) or {@code EXPENSE_INCOME_DETAIL} (GV). */
    FormSection gridSection();

    /** Every column the grid can show, in on-screen order; {@code kind()} separates always-shown from user-optional. */
    List<IVoucherGridColumnHeader> gridColumns();

    /** Fields on the form that exist only for this voucher (AP On Account, GV Balance / Net Amount ...). */
    default List<UiValidationFailure> extraDocumentInformation(UiValidationLevel level, UiFieldState state, boolean isEditMode) {
        return List.of();
    }

    /** Is the field's required marker expected? Keyed by the validator's component name. */
    default boolean required(String component) {
        return switch (component) {
            case "VOUCHER_NO", "OWNER_SITE", "CASH_BANK", "DATE" -> true;   // all show a red * (live, 2026-10-06)
            default -> false;
        };
    }

    /** Expected OPEN (true) / CLOSED (false) state of a section at a phase, or {@code null} to only record it.
     *  Phases: "initial", "after row fill". */
    default Boolean expectedSectionOpen(FormSection section, String phase) {
        return section == FormSection.DOCUMENT_INFORMATION || section == gridSection() ? Boolean.TRUE : null;
    }

    /** The sub-ledger component whose business fill is observed ({@link SubLedgerFillListener}); null = no observation. */
    default com.gsl.pages.pagecomponent.Finance.Operations.Common.VoucherSubLedgerInformationBase subLedger() {
        return null;
    }

    /** Business-key label of grid row {@code rowNumber} (1-based), e.g. "Sub Ledger: X · Reference Site: Y". */
    default String rowIdentity(int rowNumber) {
        return "";
    }

    /** Does the voucher have a Narration / Sub Ledger Balance field pair outside the grid? Probed by label;
     *  absent fields are reported as SKIP, so this only turns the whole group off. */
    default boolean hasNarrationAndSubLedgerBalance() {
        return true;
    }
}
