package com.gsl.enums;

import com.gsl.pages.enums.Finance.Operations.APVoucher.APVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.ARVoucher.ARVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.CommonVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.IVoucherGridColumnHeader;
import com.gsl.pages.enums.Finance.Operations.Common.VoucherDocumentLabels;

import java.util.Optional;

public enum VoucherDomain implements VoucherDocumentLabels {

    AR {
        @Override public String getDateFieldId() { return "transanctionDate"; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { return ARVoucherGridColumnHeader.SUB_LEDGER; }
    },
    AP {
        @Override public String getDateFieldId() { return "transactionDate"; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { return APVoucherGridColumnHeader.SUB_LEDGER; }
    },
    CONTRA {
        @Override public String getDateFieldId() { return "transactionDate"; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { throw new UnsupportedOperationException("CONTRA has no sub-ledger column header"); }
    },
    GV {
        @Override public String getDateFieldId() { return "transactionDate"; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { throw new UnsupportedOperationException("GV has no sub-ledger column header"); }
    },
    COMMON {
        @Override public String getDateFieldId() { return null; }
        @Override public IVoucherGridColumnHeader getSubLedgerColumnHeader() { throw new UnsupportedOperationException("COMMON has no sub-ledger column header"); }
    };

    public abstract IVoucherGridColumnHeader getSubLedgerColumnHeader();

    /**
     * Override provision for domain-specific common column headers.
     * Returns Optional.empty() by default — all domains use the common header.
     * A domain constant can override this method to substitute a domain-specific
     * IVoucherGridColumnHeader for a given CommonVoucherGridColumnHeader column.
     */
    public Optional<IVoucherGridColumnHeader> overrideCommonColumn(CommonVoucherGridColumnHeader col) {
        return Optional.empty();
    }
}
