package com.qms.common.enums;

/**
 * 检验任务状态
 */
public final class InspectionTaskStatus {
    private InspectionTaskStatus() {
    }

    public static final String PENDING_ASSIGN = "PENDING_ASSIGN";
    public static final String PENDING_INSPECT = "PENDING_INSPECT";
    public static final String INSPECTING = "INSPECTING";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String JUDGED = "JUDGED";
    public static final String RECHECKING = "RECHECKING";
    public static final String CLOSED = "CLOSED";

    public static final String ACT_ASSIGN = "ASSIGN";
    public static final String ACT_START = "START";
    public static final String ACT_SUBMIT = "SUBMIT";
    public static final String ACT_REJECT = "REJECT";
    public static final String ACT_PASS = "PASS";
    public static final String ACT_RECHECK = "RECHECK";
    public static final String ACT_CLOSE = "CLOSE";
}
