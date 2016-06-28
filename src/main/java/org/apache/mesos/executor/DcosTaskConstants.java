package org.apache.mesos.executor;

/**
 * This class encapsulates constants associated with DC/OS Tasks.
 */
public class DcosTaskConstants {
    public static final String TASK_TYPE = "TASK_TYPE";

}

// Error Codes
enum ExecutorErrorCode {
    EXIT_ON_TERMINATINON,
    ON_REGISTERED_TASK_FAILURE,
    ON_REREGISTERED_TASK_FAILURE
}
