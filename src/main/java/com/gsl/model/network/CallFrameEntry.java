package com.gsl.model.network;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CallFrameEntry {
    private String functionName;
    private String scriptUrl;
    private int    lineNumber;
    private int    columnNumber;
}
