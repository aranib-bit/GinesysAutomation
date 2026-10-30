package com.gsl.api.webapi.model.request.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/** Query string of {@code GET Session/Logout}; {@code sessionId} is the literal {@code null} on a single-tenant deployment. */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogoutQuery {
    @JsonProperty("_dc")
    private final long cacheBuster;
    @JsonProperty("userId")
    private final String userId;
    @JsonProperty("sessionId")
    private final String sessionId;
}
