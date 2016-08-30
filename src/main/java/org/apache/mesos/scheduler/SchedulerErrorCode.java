package org.apache.mesos.scheduler;

/**
 * This enum provides exit codes for Schedulers.
 */
public enum SchedulerErrorCode {
    SUCCESS,
    REGISTRATION_FAILURE,
    RE_REGISTRATION,
    OFFER_RESCINDED,
    FRAMEWORK_MESSAGE,
    DISCONNECTED,
    ERROR,
    PLAN_CREATE_FAILURE
}
