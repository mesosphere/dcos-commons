package com.mesosphere.sdk.scheduler;

/**
 * This enum provides exit codes for Schedulers.
 */
public enum SchedulerErrorCode {
    SUCCESS,
    INITIALIZATION_FAILURE,
    REGISTRATION_FAILURE,
    RE_REGISTRATION,
    OFFER_RESCINDED,
    DISCONNECTED,
    ERROR,
    PLAN_CREATE_FAILURE,
    LOCK_UNAVAILABLE
}
