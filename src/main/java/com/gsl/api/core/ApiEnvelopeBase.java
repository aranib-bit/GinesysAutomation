package com.gsl.api.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * The fields every Ginesys API response carries around its {@code result}: {@code status, timestamp, message, success, dataVersion,
 * error}. Response POJOs extend it and add their typed {@code result}.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class ApiEnvelopeBase {
    private int status;
    private String timestamp;
    private String message;
    private boolean success;
    private Object dataVersion;
    private Object error;
}
