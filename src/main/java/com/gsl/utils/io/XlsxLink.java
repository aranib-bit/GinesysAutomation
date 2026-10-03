package com.gsl.utils.io;

/**
 * A hyperlink cell value for {@link XlsxBook#writeTable}: visible {@code text} plus a target.
 * {@code target} is either an absolute URL ({@code http(s):}, {@code file:}) or a path relative to
 * the workbook's own folder (written as a relative file link, so a run folder can be moved/zipped).
 */
public record XlsxLink(String text, String target) {}
