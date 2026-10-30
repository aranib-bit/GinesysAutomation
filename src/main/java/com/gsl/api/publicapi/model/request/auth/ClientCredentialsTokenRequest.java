package com.gsl.api.publicapi.model.request.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/** OAuth2 client-credentials token request of the PUBLICAPI (sent form-urlencoded to {@code publicapi.tokenUrl}). */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientCredentialsTokenRequest {
    @JsonProperty("grant_type")
    @Builder.Default
    private final String grantType = com.gsl.api.publicapi.constants.PublicApiAuthDefaults.GRANT_TYPE_CLIENT_CREDENTIALS;
    @JsonProperty("client_id")
    private final String clientId;
    @JsonProperty("client_secret")
    @ToString.Exclude
    private final String clientSecret;

    public static ClientCredentialsTokenRequest of(String clientId, String clientSecret) {
        return ClientCredentialsTokenRequest.builder().clientId(clientId).clientSecret(clientSecret).build();
    }
}
