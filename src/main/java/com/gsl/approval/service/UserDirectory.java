package com.gsl.approval.service;

import com.gsl.approval.model.DirectoryPage;
import com.gsl.approval.model.DirectoryUser;
import com.gsl.approval.service.ApprovalConfigurationException.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Every user of the application, read page 1, 2, 3, ... until the reported total is covered (never just the first page) and cached for the
 * life of this object - one scenario's lookups cost one pass over the directory. Answers "who is user code N" and "who holds role R".
 */
public class UserDirectory {

    /** Safety cap so a server that keeps returning rows cannot loop forever (200 pages x the page size). */
    private static final int MAX_PAGES = 200;

    private final UserDirectoryGateway gateway;
    private final int pageSize;
    private List<DirectoryUser> cache;

    public UserDirectory(UserDirectoryGateway gateway, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Directory page size must be at least 1");
        }
        this.gateway = gateway;
        this.pageSize = pageSize;
    }

    public Optional<DirectoryUser> byUserCode(int userCode) {
        return all().stream().filter(u -> u.getUserCode() == userCode).findFirst();
    }

    /** The active (not extinct) users holding {@code roleId}. */
    public List<DirectoryUser> activeUsersOfRole(int roleId) {
        return all().stream().filter(u -> u.getRoleId() == roleId && !u.isExtinct()).toList();
    }

    private synchronized List<DirectoryUser> all() {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    private List<DirectoryUser> load() {
        List<DirectoryUser> users = new ArrayList<>();
        int total = Integer.MAX_VALUE;
        for (int page = 1; page <= MAX_PAGES && users.size() < total; page++) {
            DirectoryPage result = gateway.fetchPage(page, pageSize);
            if (result == null) {
                throw new ApprovalConfigurationException(Kind.INVALID_RESPONSE, "User directory page " + page + " had no result");
            }
            total = result.getTotalCount();
            if (result.getUsers().isEmpty()) {
                break;   // the server ran out before its own total: stop instead of asking for ever
            }
            users.addAll(result.getUsers());
        }
        if (users.size() < total) {
            throw new ApprovalConfigurationException(Kind.INVALID_RESPONSE,
                    "User directory is incomplete: read " + users.size() + " of " + total + " users");
        }
        return List.copyOf(users);
    }
}
