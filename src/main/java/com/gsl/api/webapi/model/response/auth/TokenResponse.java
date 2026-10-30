package com.gsl.api.webapi.model.response.auth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Response of {@code POST /token}. A successful login carries the two tokens ({@code access_token} for the authorization header,
 * {@code access_token2} for the ginesysauth header; camelCase on a multi-tenant deployment, hence the aliases); a refused one carries
 * {@code error}/{@code error_description} ("&lt;code&gt;|&lt;message&gt;|&lt;tag&gt;", see {@link LoginError}).
 */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @JsonProperty("access_token")
    @JsonAlias("accessToken")
    private String accessToken;
    @JsonProperty("access_token2")
    @JsonAlias("accessToken2")
    private String accessToken2;
    @ToString.Include
    @JsonProperty("error")
    private String error;
    @ToString.Include
    @JsonProperty("error_description")
    private String errorDescription;

    /** Reads a response body; an empty or non-JSON body (an HTML gateway error page) yields an empty response rather than an exception. */
    public static TokenResponse parse(String body) {
        try {
            TokenResponse parsed = body == null || body.isBlank() ? null : MAPPER.readValue(body, TokenResponse.class);
            return parsed != null ? parsed : new TokenResponse();
        } catch (Exception e) {
            return new TokenResponse();
        }
    }

    /** The login refusal this response carries, or null when it carries none. */
    public LoginError loginError() {
        return LoginError.parse(errorDescription);
    }
}
