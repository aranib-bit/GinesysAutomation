package com.gsl.testdata.dto;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Data;
import lombok.Getter;

@Getter
@Sheet(name="ScanItem")
public class ScanItemRow {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "Mode")
    private String mode;
    @Column(name = "Scan Mode")
    private String scanMode;
    @Column(name = "Item ID")
    private String itemId;
    @Column(name = "Quantity")
    private String quantity;
    @Column(name = "BatchNo")
    private String batchno;
    @Column(name="Mfg. Date")
    private String mfgdate;
    @Column(name = "SerialNo")
    private String serialno;
}
