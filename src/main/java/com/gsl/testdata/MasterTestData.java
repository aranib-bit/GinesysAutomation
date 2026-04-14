package com.gsl.testdata;


import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import lombok.Getter;

@Getter
public class MasterTestData {

    @Column(name = "testcasename")
    private String testcasename;
    @Column(name = "username")
    private String username;
    @Column(name = "password")
    private String password;
    @Column(name = "expectedTitle")
    private String expectedTitle;
    @Column(name = "age", converter = SafeIntegerConverter.class)
    private Integer age;
    @Column(name = "date")
    private String date;
    @Column(name = "TestCaseID")
    private String testcaseid;
    @Column(name= "InvocationCount",converter = SafeIntegerConverter.class)
    private Integer invocationcount;
}
