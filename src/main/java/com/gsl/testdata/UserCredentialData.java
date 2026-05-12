package com.gsl.testdata;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Sheet(name = "UIUsers")
public class UserCredentialData {

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "userType")
    private String userType;

    @Column(name = "executionSet")
    private String executionSet;

    @Column(name = "groupId")
    private String groupId;
}
