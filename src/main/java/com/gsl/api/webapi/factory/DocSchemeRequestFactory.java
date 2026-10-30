package com.gsl.api.webapi.factory;

import com.gsl.api.webapi.model.request.docscheme.DocMenuMapEntry;
import com.gsl.api.webapi.model.request.docscheme.DocSchemeCreateRequest;
import com.gsl.api.webapi.model.request.docscheme.DocSchemeListRequest;
import com.gsl.api.webapi.model.request.docscheme.SiteCodeQuery;
import com.gsl.api.webapi.model.request.docscheme.SiteEntityQuery;
import com.gsl.api.webapi.model.request.docscheme.UntaggedSchemesRequest;

import java.util.List;

import static com.gsl.api.webapi.constants.DocSchemeDefaults.*;

/** Builds the numbering-scheme admin request bodies and query strings; no HTTP, no shared state. */
public final class DocSchemeRequestFactory {

    private DocSchemeRequestFactory() {}

    public static DocSchemeListRequest list(String workDate) {
        return DocSchemeListRequest.builder().pagingNotRequired(true).workDate(workDate).page(1).start(0).limit(LIST_PAGE_SIZE).build();
    }

    /** A yearly, OU-wise, auto-numbered scheme; the sample number is the prefix, {@code width} digits ending in the starting number, and the year suffix. */
    public static DocSchemeCreateRequest create(String docName, String prefix, int width, String remarks, String yearSuffix, String periodFrom, String periodTo) {
        String sample = prefix + SAMPLE_ZERO.repeat(Math.max(0, width - 1)) + "1/" + yearSuffix;
        return DocSchemeCreateRequest.builder()
                .alreadyUsed(false).prefill(PREFILL).width(width).starting(STARTING_NUMBER)
                .excise(NO).extinctFlag(NO).generationInterval(GENERATION_INTERVAL_YEARLY).generationBasis(GENERATION_BASIS_OU).gstApplBacking(NO)
                .docCodeWise(NO).basedOn(BASED_ON_SCHEME).docName(docName).prefix(prefix).defaultSuffix(EMPTY).suffix(YEAR_SUFFIX)
                .defaultSuffixMusk(EMPTY).suffixAttachment(EMPTY).seperator1(EMPTY).seperator2(EMPTY).remarks(remarks).manual(NO)
                .dateOfCreation(null).dateOfModification(null).sampleDocNo(sample).companyCode(0).systemGenerated(EMPTY).code(0).gstAppl(EMPTY)
                .accPeriodDateFrom(periodFrom).accPeriodDateTo(periodTo).validSampleNo(true).menuTagged(false).extinct(false).exciseApp(false)
                .extinctHidden(true)
                .build();
    }

    /** One tag entry per menu shortcode ({@code onlyDelete=false}). */
    public static List<DocMenuMapEntry> tag(long docCode, List<String> menuShortcodes) {
        return menuShortcodes.stream()
                .map(shortcode -> DocMenuMapEntry.builder().docCode(docCode).menuCode(null).menuShCode(shortcode).onlyDelete(false).build())
                .toList();
    }

    public static UntaggedSchemesRequest untagged(String workDate) {
        return UntaggedSchemesRequest.builder().workDate(workDate).page(1).start(0).limit(LIST_PAGE_SIZE).build();
    }

    public static SiteCodeQuery siteCode(long siteCode) {
        return SiteCodeQuery.builder().siteCode(siteCode).build();
    }

    public static SiteEntityQuery siteEntity(long code) {
        return SiteEntityQuery.builder().code(code).build();
    }
}
