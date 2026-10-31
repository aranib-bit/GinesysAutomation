package com.gsl.testdata;

/** How an account signs in: {@code ERP} is the application's own login ({@code /token}); {@code SSO} goes through the single-sign-on provider first. */
public enum LoginType {
    ERP, SSO
}
