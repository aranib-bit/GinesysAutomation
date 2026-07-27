package com.gsl.model.adm;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Maps to admyear — fiscal year calendar (ycode -> [dtfr, dtto] date range).
 */
@Data
public class AdmYear {
    private long ycode;
    private String yname;
    private LocalDateTime dtfr;
    private LocalDateTime dtto;
}
