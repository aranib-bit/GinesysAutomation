package com.gsl.model.network;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestCookieEntry {
    private String name;
    private String value;
    private String domain;
    private String path;
    private String expires;
    private int    size;
    private boolean httpOnly;
    private boolean secure;
    private String sameSite;
    private String priority;
}
