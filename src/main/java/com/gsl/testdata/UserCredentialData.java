package com.gsl.testdata;

import io.github.sskorol.data.Column;
import io.github.sskorol.data.Sheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Excel-row DTO shaped for a {@code UIUsers}-sheet credential row (username/password/userType/
 * executionSet/groupId), for use with {@code io.github.sskorol}'s sheet-to-POJO mapping.
 * TODO-confirm: no call site in this codebase currently constructs or reads this class — the
 * live credential path is {@link UserCredentialLoader#loadAll()}, which reads the same columns
 * directly via Apache POI into {@link UserCredential} records rather than through this DTO. Do
 * not assume it is dead code without checking for newer callers first; it may be a
 * scaffold/alternate loading path left in place alongside the POI-based one.
 */
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
