package com.gsl.approval.model;

/** Whether the approved document ended up posted. {@code UNKNOWN} when the environment gives no way to read it (no database access). */
public enum PostedState { POSTED, UNPOSTED, UNKNOWN }
