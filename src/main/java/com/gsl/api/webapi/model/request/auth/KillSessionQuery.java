package com.gsl.api.webapi.model.request.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/** Query string of {@code GET Session/KillUser}: ends the session a code-012 login refusal named. */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KillSessionQuery {
    @JsonProperty("userId")
    private final String userId;

    public static KillSessionQuery of(String userId) {
        return KillSessionQuery.builder().userId(userId).build();
    }
}
