package com.testdata;


import io.github.sskorol.converters.IntegerConverter;
import io.github.sskorol.data.Column;

public class TestData {

    @Column(name = "testcasename")
    public String testcasename;
    @Column(name = "username")
    public String username;
    @Column(name = "password")
    public String password;
    @Column(name = "expectedTitle")
    public String expectedTitle;
    @Column(name = "age", converter = IntegerConverter.class)
    public int age;
    @Column(name = "date")
    public String StartDate;

}
