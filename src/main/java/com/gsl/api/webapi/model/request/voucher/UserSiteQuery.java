package com.gsl.api.webapi.model.request.voucher;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Query of {@code POST /UserRolePolicy/GetAllSiteForAUser}: {@code userId=0} means the signed-in user. */
@Getter
@AllArgsConstructor
public class UserSiteQuery {
    @JsonProperty("userId")
    private final long userId;

    public static UserSiteQuery currentUser() {
        return new UserSiteQuery(0);
    }
}
