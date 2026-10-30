package com.gsl.api.publicapi.model.response.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** Response of the PUBLICAPI OAuth2 token endpoint. */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OAuthTokenResponse {
    @JsonProperty("access_token")
    private String accessToken;
    @ToString.Include
    @JsonProperty("token_type")
    private String tokenType;
    @ToString.Include
    @JsonProperty("expires_in")
    private Integer expiresIn;

    /** Seconds the token stays valid, or 0 when the server did not say (the token is then refetched on every use). */
    public int expiresInSeconds() {
        return expiresIn == null ? 0 : expiresIn;
    }
}
