package com.gsl.testdata.Procurement;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Getter;

/**
 * Fixture-data counterpart of the "General" sheet of the Receipts workbook ({@code
 * ConfigFactory.getExcelDataConfig().receiptsPath()}) — one Goods Receipt Challan's header
 * (doc scheme/receipt no., vendor, stock point, owner site, term, trade group). Joined to its
 * {@code MasterTestData} row by {@code com.gsl.utils.dataprovider.MergedReceiptDataSupplier}
 * (mandatory — throws if a matched {@code TestCaseID} has no row here) into {@code
 * com.gsl.testdata.dto.MergedReceiptData.receiptHeader}.
 */
@Getter
@Sheet(name = "General")
public class ReceiptHeader {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "Receipt No")
    private String receiptNo;
    @Column(name = "Vendor Name")
    private String vendorName;
    @Column(name = "Vendor Document No")
    private String vendorDocumentNo;
    @Column(name = "Stock Point")
    private String stockPoint;
    @Column(name="Owner Site")
    private String ownersite;
    @Column(name="Term Name")
    private String termName;
    @Column(name = "Trade Group Name")
    private String tradeGroupName;

}
