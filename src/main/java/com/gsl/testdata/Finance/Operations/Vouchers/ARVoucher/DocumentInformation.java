package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.ItcEligibilityConverter;
import com.gsl.converter.SafeDateConverter;
import com.gsl.converter.SafeIntegerConverter;
import com.gsl.enums.ItcEligibilityType;
import com.gsl.testdata.Finance.Operations.Vouchers.Common.VoucherDocumentHeader;
import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.MandatoryType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


/**
 * Test-data model for the AR Voucher "DocumentInformation" sheet — the voucher header fields
 * (owner site, voucher no., cash/bank, remarks, date, reference). Unlike AP, AR has no "On
 * Account" concept, so this class implements {@link VoucherDocumentHeader} directly with no
 * base class.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
    @Sheet(name= "DocumentInformation")
    public class DocumentInformation implements VoucherDocumentHeader {
        @Mandatory
        @Column(name = "TestCaseID")
        private String testCaseId;
        @Column(name="Owner Site")
        private String ownersite;
        @Column(name ="Voucher No")
        private String voucherno;
        @Column(name="Cash/Bank")
        private String cashbank;
        @Column(name = "Remarks")
        private String remarks;
        @Mandatory
        @Column(name="Date",converter = SafeDateConverter.class)
        private LocalDate documentdate;
        @Column(name = "Reference")
        private String reference;
}
