package com.gsl.approval.service;

import com.gsl.approval.model.DirectoryPage;

/** Reads one page of the application's user directory. The only way {@link UserDirectory} reaches it, so it can be faked in tests. */
public interface UserDirectoryGateway {

    /** Page {@code page} (1-based) of {@code limit} users; {@code null} when the application returned none. */
    DirectoryPage fetchPage(int page, int limit);
}
