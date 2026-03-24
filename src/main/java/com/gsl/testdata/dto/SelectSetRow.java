package com.gsl.testdata.dto;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.Data;
import lombok.Getter;

@Getter
@Sheet(name="SelectSet")
public class SelectSetRow {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "SetID")
    private String setId;

}
