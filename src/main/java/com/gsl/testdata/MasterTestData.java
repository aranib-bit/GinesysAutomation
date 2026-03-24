package com.gsl.testdata;


import com.gsl.converter.SafeIntegerConverter;
import io.github.sskorol.data.Column;
import lombok.Getter;

@Getter
public class MasterTestData {

    @Column(name = "testcasename")
    public String testcasename;
    @Column(name = "username")
    public String username;
    @Column(name = "password")
    public String password;
    @Column(name = "expectedTitle")
    public String expectedTitle;
    @Column(name = "age", converter = SafeIntegerConverter.class)
    public Integer age;
    @Column(name = "date")
    public String date;
    @Column(name = "TestCaseID")
    public String testcaseid;
    @Column(name= "InvocationCount",converter = SafeIntegerConverter.class)
    public Integer invocationcount;
}
