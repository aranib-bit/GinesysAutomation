package com.gsl.api.webapi.model.response.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

/** Response of {@code GET Session/Logout}: only the {@code success} flag matters. */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogoutResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Boolean success;

    public static LogoutResponse parse(String body) {
        try {
            LogoutResponse parsed = body == null || body.isBlank() ? null : MAPPER.readValue(body, LogoutResponse.class);
            return parsed != null ? parsed : new LogoutResponse();
        } catch (Exception e) {
            return new LogoutResponse();
        }
    }

    public boolean succeeded() {
        return Boolean.TRUE.equals(success);
    }
}
