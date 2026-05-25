package com.gsl.model.network;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ServerTimingEntry {
    private String metricName;
    private String description;
    private double duration;
}
