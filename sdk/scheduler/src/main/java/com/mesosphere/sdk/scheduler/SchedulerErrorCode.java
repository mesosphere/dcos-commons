package com.mesosphere.sdk.scheduler;

/**
 * This enum provides exit codes for Schedulers.
 */
public class SchedulerErrorCode {

    public static final SchedulerErrorCode SUCCESS = new SchedulerErrorCode(0);
    public static final SchedulerErrorCode INITIALIZATION_FAILURE = new SchedulerErrorCode(1);
    public static final SchedulerErrorCode REGISTRATION_FAILURE = new SchedulerErrorCode(2);
    // No longer used:
    //public static final SchedulerErrorCode RE_REGISTRATION = new SchedulerErrorCode(3);
    public static final SchedulerErrorCode OFFER_RESCINDED = new SchedulerErrorCode(4);
    public static final SchedulerErrorCode DISCONNECTED = new SchedulerErrorCode(5);
    public static final SchedulerErrorCode ERROR = new SchedulerErrorCode(6);
    public static final SchedulerErrorCode PLAN_CREATE_FAILURE = new SchedulerErrorCode(7);
    public static final SchedulerErrorCode LOCK_UNAVAILABLE = new SchedulerErrorCode(8);
    public static final SchedulerErrorCode API_SERVER_TIMEOUT = new SchedulerErrorCode(9);
    public static final SchedulerErrorCode SCHEDULER_BUILD_FAILED = new SchedulerErrorCode(10);
    public static final SchedulerErrorCode SCHEDULER_ALREADY_UNINSTALLING = new SchedulerErrorCode(11);

    private final int value;

    private SchedulerErrorCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
