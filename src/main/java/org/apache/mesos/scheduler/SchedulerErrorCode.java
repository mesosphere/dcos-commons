package org.apache.mesos.scheduler;

/**
 * Created by gabriel on 8/25/16.
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
