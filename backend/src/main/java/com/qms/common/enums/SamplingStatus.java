package com.qms.common.enums;

/**
 * 抽样单状态
 */
public final class SamplingStatus {
    private SamplingStatus() {
    }

    public static final String DRAFT = "DRAFT";
    public static final String PENDING_RECEIVE = "PENDING_RECEIVE";
    public static final String RECEIVED = "RECEIVED";
    public static final String ARCHIVED = "ARCHIVED";
    public static final String CANCELLED = "CANCELLED";

    public static final String ACT_SUBMIT = "SUBMIT";
    public static final String ACT_RECEIVE = "RECEIVE";
    public static final String ACT_CANCEL = "CANCEL";
    public static final String ACT_ARCHIVE = "ARCHIVE";
}
