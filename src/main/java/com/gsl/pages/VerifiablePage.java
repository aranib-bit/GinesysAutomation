package com.gsl.pages;

/**
 * Implemented by page objects that can confirm, via a live DOM signal (not URL, not menu-highlight
 * state), that the browser is actually showing them right now. {@code NavigationUtils} checks for
 * this after a navigation call lands — if the returned page implements it, the check runs before
 * the caller is handed a page object it might otherwise trust blindly (e.g. before clicking a
 * generic, page-agnostic "Add" button that exists on many list pages).
 */
public interface VerifiablePage {

    /** @throws IllegalStateException if the live page doesn't match what this page object expects. */
    void verifyOnThisPage();
}
