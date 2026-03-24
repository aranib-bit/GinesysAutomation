package com.gsl.testdata.dto;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Data;

@Data
@Sheet(name="Scan Set")
public class ScanSetRow {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "Scan Set Detail")
    private String scanDetail;
}
