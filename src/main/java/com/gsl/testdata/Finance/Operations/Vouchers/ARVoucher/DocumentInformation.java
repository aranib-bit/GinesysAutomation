package com.gsl.testdata.Finance.Operations.Vouchers.ARVoucher;

import com.gsl.converter.ItcEligibilityConverter;
import com.gsl.converter.SafeDateConverter;
import com.gsl.converter.SafeIntegerConverter;
import com.gsl.enums.ItcEligibilityType;
import com.gsl.testdata.annotations.Mandatory;
import com.gsl.testdata.annotations.MandatoryType;
import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

import java.time.LocalDate;


@Getter
    @Sheet(name= "DocumentInformation")
    public class DocumentInformation {
        @Mandatory
        @Column(name = "TestCaseID")
        public String testCaseId;
        @Column(name="Owner Site")
        public String ownersite;
        @Column(name ="Voucher No")
        public String voucherno;
        @Column(name="Cash/Bank")
        public String cashbank;
        @Column(name = "Remarks")
        public String remarks;
        @Mandatory
        @Column(name="Date",converter = SafeDateConverter.class)
        public LocalDate documentdate;
        @Column(name = "Reference")
        public String reference;
}
