package com.gsl.api.webapi.model.request.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/** {@code POST /token} form: the OAuth2 password grant of the WebAPI login (sent form-urlencoded). */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PasswordTokenRequest {
    @JsonProperty("grant_type")
    @Builder.Default
    private final String grantType = com.gsl.api.webapi.constants.AuthDefaults.GRANT_TYPE_PASSWORD;
    @JsonProperty("username")
    private final String username;
    @JsonProperty("password")
    @ToString.Exclude
    private final String password;

    public static PasswordTokenRequest of(String username, String password) {
        return PasswordTokenRequest.builder().username(username).password(password).build();
    }
}
