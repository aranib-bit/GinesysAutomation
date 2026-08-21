package com.gsl.enums;

import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherActionsMenu;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.FinanceVoucherListPageHeader;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridAction;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherListColumn;
import com.gsl.pages.enums.Finance.Operations.Common.VoucherDocumentLabels;
import com.gsl.pages.enums.Finance.Operations.GeneralVoucher.GeneralVoucherGridColumnHeader;
import com.gsl.validation.snapshot.TableRule;

import java.util.List;
import java.util.Optional;

public enum VoucherDomain implements VoucherDocumentLabels {

    AR {
        @Override public String getDateFieldId() { return "transanctionDate"; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { return ARVoucherGridColumnHeader.SUB_LEDGER; }
        @Override public FinanceVoucherListPageHeader getListPageHeaderEnum() { return FinanceVoucherListPageHeader.AR; }
        /** TODO-confirm against {@code mdmenu.sys_shortcode} — same open item as GV's (plan §2/§4/§9). */
        @Override public String getMenuShortcode() { return "FinanceARVoucher"; }
        @Override public VoucherType getVoucherType() { return VoucherType.AR; }
    },
    AP {
        @Override public String getDateFieldId() { return "transactionDate"; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { return APVoucherGridColumnHeader.SUB_LEDGER; }
        @Override public FinanceVoucherListPageHeader getListPageHeaderEnum() { return FinanceVoucherListPageHeader.AP; }
        /** TODO-confirm against {@code mdmenu.sys_shortcode} — same open item as GV's (plan §2/§4/§9). */
        @Override public String getMenuShortcode() { return "FinanceAPVoucher"; }
        @Override public VoucherType getVoucherType() { return VoucherType.AP; }
    },
    CONTRA {
        @Override public String getDateFieldId() { return "transactionDate"; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { throw new UnsupportedOperationException("CONTRA has no sub-ledger column header"); }
        @Override public String getVoucherLabel() { return "Contra Voucher"; }
        @Override public FinanceVoucherListPageHeader getListPageHeaderEnum() { return FinanceVoucherListPageHeader.CONTRA; }
        @Override public String getMenuShortcode() { throw new UnsupportedOperationException("CONTRA has no menu shortcode resolved yet"); }
        @Override public VoucherType getVoucherType() { return VoucherType.CONTRA; }
    },
    GV {
        @Override public String getDateFieldId() { return "transanctionDate"; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { return GeneralVoucherGridColumnHeader.SUB_LEDGER; }
        @Override public String getVoucherLabel() { return "General Voucher"; }
        @Override public FinanceVoucherListPageHeader getListPageHeaderEnum() { return FinanceVoucherListPageHeader.GV; }
        /** TODO-confirm against {@code mdmenu.sys_shortcode} — same open item flagged in plan §2/§4/§9. */
        @Override public String getMenuShortcode() { return "FinanceGeneralVoucher"; }
        @Override public VoucherType getVoucherType() { return VoucherType.GENERAL; }
    },
    COMMON {
        @Override public String getDateFieldId() { return null; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { throw new UnsupportedOperationException("COMMON has no sub-ledger column header"); }
        @Override public FinanceVoucherListPageHeader getListPageHeaderEnum() { throw new UnsupportedOperationException("COMMON has no list page"); }
        @Override public String getMenuShortcode() { throw new UnsupportedOperationException("COMMON has no menu shortcode"); }
        @Override public VoucherType getVoucherType() { throw new UnsupportedOperationException("COMMON has no voucher type"); }
    };

    public abstract IVoucherGridColumnHeader getSubLedgerColumnHeader();

    /** Returns the {@link FinanceVoucherListPageHeader} constant carrying this domain's literal
     *  list-page header text — the single source of truth {@link #getListPageHeader()} delegates to. */
    public abstract FinanceVoucherListPageHeader getListPageHeaderEnum();

    /**
     * Returns this domain's {@code mdmenu.sys_shortcode} value, e.g. {@code "FinanceARVoucher"} —
     * the menu-shortcode half of {@link IVoucherGridAction#permissionKey(VoucherDomain)}'s key
     * (plan §9.3). AR/AP/GV values are TODO-confirm guesses (same open item already flagged for
     * GV in plan §2/§4); CONTRA/COMMON throw {@link UnsupportedOperationException}, matching how
     * they already handle other unsupported concepts (e.g. {@link #getSubLedgerColumnHeader()}).
     */
    public abstract String getMenuShortcode();

    /**
     * Returns this domain's {@link VoucherType} — the {@code finvchmain.vchtype} discriminator
     * that lets the common snapshot/diff wiring layer (plan §2) resolve/label a document without
     * any domain-specific SQL. {@code AR}/{@code AP}/{@code GV} map onto {@code VoucherType}'s
     * existing {@code AR}/{@code AP}/{@code GENERAL} constants (same {@code vchtype} codes
     * {@code DbGeneralVoucherEditDataSource}'s own claim query already filters on);
     * {@code CONTRA} maps onto {@code VoucherType.CONTRA}; {@code COMMON} throws, same convention
     * as {@link #getMenuShortcode()}.
     */
    public abstract VoucherType getVoucherType();

    /**
     * Extra persistence-map tables beyond the common {@code finvchmain}/{@code finvchdet}/
     * {@code finpost}/{@code fincosttag} base (plan §2) — empty by default. No domain uses this
     * today (not even GV — {@code fincosttag} turned out to be common, not GV-specific, once its
     * participation condition was traced back to a plain {@code finvchdet.gl_cc_appl} column
     * rather than anything GV-owned); reserved for a genuinely per-domain table if one is ever
     * found, same future-proofing convention as the (also currently all-default)
     * {@link #overrideCommonListColumn}/{@link #overrideCommonAction} hooks.
     */
    public List<TableRule> additionalPersistenceTables() {
        return List.of();
    }

    /**
     * Override provision for a domain whose {@code finvchdet}/{@code finpost} (or other common
     * persistence-map table) business-key or column rules differ from the shared base — e.g. a
     * domain whose sub-ledger columns need a wider business key than {@code [vchcode, glcode]}.
     * {@code Optional.empty()} by default, meaning "use the common {@link TableRule} as-is" —
     * mirrors {@link #overrideCommonListColumn}/{@link #overrideCommonAction} exactly, for
     * persistence-map tables instead of grid columns/actions.
     */
    public Optional<TableRule> overridePersistenceTableRule(String tableName) {
        return Optional.empty();
    }

    /**
     * Override provision for domain-specific common column headers.
     * Returns Optional.empty() by default — all domains use the common header.
     * A domain constant can override this method to substitute a domain-specific
     * IVoucherGridColumnHeader for a given CommonVoucherGridColumnHeader column.
     */
    public Optional<IVoucherGridColumnHeader> overrideCommonColumn(CommonVoucherGridColumnHeader col) {
        return Optional.empty();
    }

    /**
     * Override provision for a domain whose grid uses a differently-shaped version of a
     * shared list-grid column (different header text and/or {@link
     * com.gsl.pages.enums.Finance.Operations.Common.ColumnDataType} and/or mandatory-ness).
     * Returns {@code Optional.empty()} by default — the domain uses {@code col} as-is.
     * A domain constant overrides this method to substitute a domain-specific
     * {@link IVoucherListColumn} for a given {@link CommonVoucherListColumn}.
     */
    public Optional<IVoucherListColumn> overrideCommonListColumn(CommonVoucherListColumn col) {
        return Optional.empty();
    }

    /**
     * Returns {@code true} if a domain's grid doesn't have {@code col} at all (as opposed to
     * having a differently-shaped version of it — see {@link #overrideCommonListColumn}).
     * Returns {@code false} by default — every domain has every common column.
     */
    public boolean excludesCommonListColumn(CommonVoucherListColumn col) {
        return false;
    }

    /**
     * Override provision for domain-specific Actions-menu items. Returns {@code Optional.empty()}
     * by default — all domains use the common action as-is. A domain constant can override this
     * method to substitute a domain-specific {@link IVoucherGridAction} for a given
     * {@link CommonVoucherActionsMenu} entry. Mirrors {@link #overrideCommonListColumn} exactly,
     * for actions instead of columns (plan §9.3).
     */
    public Optional<IVoucherGridAction> overrideCommonAction(CommonVoucherActionsMenu action) {
        return Optional.empty();
    }

    /**
     * Returns {@code true} if a domain's Actions dropdown doesn't have {@code action} at all (as
     * opposed to having a differently-shaped version of it — see {@link #overrideCommonAction}).
     * Returns {@code false} by default — every domain has every common action. Mirrors
     * {@link #excludesCommonListColumn} exactly, for actions instead of columns (plan §9.3).
     */
    public boolean excludesCommonAction(CommonVoucherActionsMenu action) {
        return false;
    }

    /** Returns a human-readable voucher label, e.g. "AP Voucher", "AR Voucher". */
    public String getVoucherLabel() {
        return name() + " Voucher";
    }

    /**
     * Returns the expected list-page header text, e.g. "AP Vouchers", "General Vouchers" —
     * the live-DOM signal {@code VoucherListGridHeaderBase#verifyOnThisPage()} checks against.
     * Delegates to {@link #getListPageHeaderEnum()} — {@link FinanceVoucherListPageHeader} is
     * the single source of truth for this literal text, not a computed string.
     */
    public String getListPageHeader() {
        return getListPageHeaderEnum().headerText();
    }
}
