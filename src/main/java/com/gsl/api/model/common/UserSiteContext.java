package com.gsl.api.model.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * {@code result} of {@code POST /WebAPI/api/UserRolePolicy/GetAllSiteForAUser?userId=0} (body {@code {}}) — the per-user
 * context the UI loads right after login: assigned sites (with type and OU), the connected OU, accounting years and the
 * profile flags (e.g. {@code finCanRelease}, {@code finCanRevert}, {@code finAutoRelease}) plus
 * {@code allowAuthorizeOfSelfCreatedTransaction} (the maker-checker setting). Confirmed via HAR (Part F.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSiteContext {

    private List<Site> sites;
    private long oucode;
    private String ouName;
    private String loginDate;
    /** Profile id/name/flags as returned ({@code code}, {@code profName}, {@code finCanRelease}, …). */
    private Map<String, Object> profileAccess;
    /** {@code "Y"} = a user may authorize a transaction they created (maker-checker NOT enforced). */
    private String allowAuthorizeOfSelfCreatedTransaction;

    /** True when the profile flag {@code name} equals {@code Y}. */
    public boolean profileFlag(String name) {
        Object v = profileAccess == null ? null : profileAccess.get(name);
        return v != null && "Y".equalsIgnoreCase(String.valueOf(v));
    }

    /** First assigned site of type {@code OS-OO-CM} (the owner-site type General Voucher uses), or null. */
    public Site firstOwnerSite() {
        if (sites == null) return null;
        return sites.stream().filter(s -> "OS-OO-CM".equalsIgnoreCase(s.getSiteType())).findFirst().orElse(null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Site {
        private long siteCode;
        private String siteName;
        private String shortName;
        private String siteType;
        private long ouCode;
        private String ouName;
    }
}
