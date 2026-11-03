package com.gsl.api.webapi.model.response.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.gsl.api.core.ApiEnvelopeBase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Response of {@code POST GlobalUser/GetListOfGlobalAppEmpUsers}. The row also carries the user's password ({@code pass}); it is deliberately
 * NOT mapped here, so it can never reach a log, a report or the approval model.
 */
@Getter
@Setter
public class GlobalUserListResponse extends ApiEnvelopeBase {
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Integer totalCount;
        private List<Item> items;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        /** The user's code (sent as {@code 1022.0}). */
        private Double ecode;
        /** The user's employee number (not always the login name). */
        private String eno;
        /** The user's abbreviation, which is the login name for some users (e.g. eno 7 logs in as bt). */
        private String abbre;
        private String name;
        /** The role id (sent as {@code 1019.0}). */
        private Double rlcode;
        private String rlname;
        /** {@code Y} for an extinct (disabled) user. */
        private String ext;
        private String userType;
    }
}
