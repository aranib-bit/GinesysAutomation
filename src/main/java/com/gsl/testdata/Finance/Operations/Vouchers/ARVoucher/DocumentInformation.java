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
