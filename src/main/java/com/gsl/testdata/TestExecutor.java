package com.gsl.testdata;

import io.github.sskorol.data.Column;

public class TestExecutor {
    @Column(name = "TestCaseID")
    private String testCaseId;
    @Column(name = "testcasename")
    private String testcasename;
    @Column(name = "username")
    private String username;
    @Column(name = "password")
    private String password;
    @Column(name = "expectedTitle")
    private String expectedTitle;
    @Column(name = "age")
    private String age;
    @Column(name = "date")
    private String date;

    public String getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getTestcasename() {
        return testcasename;
    }

    public void setTestcasename(String testcasename) {
        this.testcasename = testcasename;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getExpectedTitle() {
        return expectedTitle;
    }

    public void setExpectedTitle(String expectedTitle) {
        this.expectedTitle = expectedTitle;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }
}
