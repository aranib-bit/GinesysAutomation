package com.gsl.api.webapi.model.response.voucher;

import com.gsl.api.core.ApiEnvelopeBase;
import com.gsl.api.model.common.UserSiteContext;
import lombok.Getter;
import lombok.Setter;

/** Response of {@code POST UserRolePolicy/GetAllSiteForAUser}: the user's sites, OU and profile flags. */
@Getter
@Setter
public class UserSiteContextResponse extends ApiEnvelopeBase {
    private UserSiteContext result;
}
