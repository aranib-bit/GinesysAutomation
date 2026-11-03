package com.gsl.approval.api;

import com.gsl.api.webapi.client.ApprovalApiClient;
import com.gsl.api.webapi.model.request.approval.GlobalUserListRequest;
import com.gsl.api.webapi.model.response.approval.GlobalUserListResponse;
import com.gsl.approval.model.DirectoryPage;
import com.gsl.approval.model.DirectoryUser;
import com.gsl.approval.service.UserDirectoryGateway;

import java.util.List;

/** {@link UserDirectoryGateway} over {@link ApprovalApiClient}. The row's password field is not part of the response POJO, so it is never read. */
public class ApiUserDirectoryGateway implements UserDirectoryGateway {

    private final ApprovalApiClient client;
    private final String connectedSite;
    private final int connectedSiteOuCode;
    private final String workDate;

    /** @param workDate the application's date format, e.g. {@code 2026-11-03T00:00:00} */
    public ApiUserDirectoryGateway(ApprovalApiClient client, String connectedSite, int connectedSiteOuCode, String workDate) {
        this.client = client;
        this.connectedSite = connectedSite;
        this.connectedSiteOuCode = connectedSiteOuCode;
        this.workDate = workDate;
    }

    @Override
    public DirectoryPage fetchPage(int page, int limit) {
        GlobalUserListResponse.Result result = ApprovalApiCalls.read("Reading user directory page " + page,
                () -> client.getUserPage(GlobalUserListRequest.page(connectedSite, connectedSiteOuCode, workDate, page, limit)));
        if (result == null) {
            return null;
        }
        List<DirectoryUser> users = result.getItems() == null ? List.of() : result.getItems().stream()
                .filter(i -> i.getEcode() != null && loginName(i) != null)
                .map(i -> new DirectoryUser(i.getEcode().intValue(), loginName(i), i.getName(),
                        i.getRlcode() == null ? 0 : i.getRlcode().intValue(), i.getRlname(), "Y".equalsIgnoreCase(i.getExt()), i.getEno()))
                .toList();
        int total = result.getTotalCount() == null ? users.size() : result.getTotalCount();
        return new DirectoryPage(total, users);
    }

    /**
     * The login name: the directory's {@code abbre} (the User Name column, unique across users). The employee number is not a login - it differs
     * for 40 of the 91 staging users - so it is only the fallback for a row with no abbreviation at all.
     */
    private static String loginName(GlobalUserListResponse.Item item) {
        if (item.getAbbre() != null && !item.getAbbre().isBlank()) {
            return item.getAbbre().trim();
        }
        return item.getEno() == null || item.getEno().isBlank() ? null : item.getEno().trim();
    }
}
