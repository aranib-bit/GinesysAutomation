package com.gsl.api.webapi.model.request.docscheme;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code POST MenuDocScheme/Create} body, as the UI records it; only the name, prefix, width, remarks, sample number and accounting period vary. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class DocSchemeCreateRequest {
    @JsonProperty("isAlreadyUsed")
    private boolean alreadyUsed;
    @JsonProperty("prefill")
    private String prefill;
    @JsonProperty("width")
    private int width;
    @JsonProperty("starting")
    private int starting;
    @JsonProperty("isExcise")
    private String excise;
    @JsonProperty("isExtinct")
    private String extinctFlag;
    @JsonProperty("generationInterval")
    private String generationInterval;
    @JsonProperty("generationBasis")
    private String generationBasis;
    @JsonProperty("gstApplBacking")
    private String gstApplBacking;
    @JsonProperty("isDocCodeWise")
    private String docCodeWise;
    @JsonProperty("basedOn")
    private String basedOn;
    @JsonProperty("docName")
    private String docName;
    @JsonProperty("prefix")
    private String prefix;
    @JsonProperty("defaultSuffix")
    private String defaultSuffix;
    @JsonProperty("suffix")
    private String suffix;
    @JsonProperty("defaultSuffixMusk")
    private String defaultSuffixMusk;
    @JsonProperty("suffixAttachment")
    private String suffixAttachment;
    @JsonProperty("seperator1")
    private String seperator1;
    @JsonProperty("seperator2")
    private String seperator2;
    @JsonProperty("remarks")
    private String remarks;
    @JsonProperty("isManual")
    private String manual;
    @JsonProperty("dateOfCreation")
    private String dateOfCreation;
    @JsonProperty("dateOfModification")
    private String dateOfModification;
    @JsonProperty("sampleDocNo")
    private String sampleDocNo;
    @JsonProperty("companyCode")
    private int companyCode;
    @JsonProperty("isSystemGenerated")
    private String systemGenerated;
    @JsonProperty("code")
    private int code;
    @JsonProperty("gstAppl")
    private String gstAppl;
    @JsonProperty("accPeriodDateFrom")
    private String accPeriodDateFrom;
    @JsonProperty("accPeriodDateTo")
    private String accPeriodDateTo;
    @JsonProperty("validSampleNo")
    private boolean validSampleNo;
    @JsonProperty("isMenuTagged")
    private boolean menuTagged;
    @JsonProperty("extinct")
    private boolean extinct;
    @JsonProperty("isExciseApp")
    private boolean exciseApp;
    @JsonProperty("isExtinctHidden")
    private boolean extinctHidden;
}
