package org.apache.mesos.executor;

/**
 * This class encapsulates constants associated with DC/OS Tasks.
 */
public class DcosTaskConstants {
    public static final String TASK_TYPE = "TASK_TYPE";
    public static final String ON_REGISTERED_TASK = "ON_REGISTERED_TASK";
    public static final String ON_REREGISTERED_TASK = "ON_REREGISTERED_TASK";

}

// Executor Error Codes
enum ExecutorErrorCode {
    EXIT_ON_TERMINATION_SUCCESS,
    EXIT_ON_TERMINATION_FAILURE
}

