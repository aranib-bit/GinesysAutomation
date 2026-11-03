package com.gsl.approval.model;

import lombok.Value;

import java.util.List;

/** One page of the user directory with the total row count the application reports. */
@Value
public class DirectoryPage {
    int totalCount;
    List<DirectoryUser> users;
}
