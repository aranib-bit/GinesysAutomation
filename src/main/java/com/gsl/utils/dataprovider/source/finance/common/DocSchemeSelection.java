package com.gsl.utils.dataprovider.source.finance.common;

import com.gsl.model.adm.AdmDocSchemeCandidate;
import lombok.Value;

import java.util.List;

/**
 * Outcome of {@link DocSchemeResolver}: the chosen scheme plus everything considered, so callers
 * can pass the name/code on and log the full decision ("Test Data Resolution").
 */
@Value
public class DocSchemeSelection {

    AdmDocSchemeCandidate chosen;
    DocSchemeMode mode;
    String menuShortcode;
    long ycode;
    long ouCode;
    /** Every scheme mapped to the menu and enabled for the user's sites, with derived capacity fields. */
    List<AdmDocSchemeCandidate> candidates;
    /** Narrative lines (no {@code [DB Query} prefix) ready to append to a data source's gathering log. */
    List<String> narrative;

    public String docName() {
        return chosen.getDocname();
    }

    public long docCode() {
        return chosen.getDoccode();
    }
}
