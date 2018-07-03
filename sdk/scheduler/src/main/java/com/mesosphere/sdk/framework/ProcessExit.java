package com.mesosphere.sdk.framework;

import com.codahale.metrics.jvm.ThreadDump;

import java.lang.management.ManagementFactory;

/**
 * This class handles exiting the Scheduler process, after completed teardown or after a fatal error.
 */
public class ProcessExit {

    // Commented items are no longer used, but their numbers may be repurposed later
    public static final Code SUCCESS = new Code(0, "SUCCESS");
    public static final Code INITIALIZATION_FAILURE = new Code(1, "INITIALIZATION_FAILURE");
    public static final Code REGISTRATION_FAILURE = new Code(2, "REGISTRATION_FAILURE");
    //public static final Code RE_REGISTRATION = new Code(3);
    //public static final Code OFFER_RESCINDED = new Code(4);
    public static final Code DISCONNECTED = new Code(5, "DISCONNECTED");
    public static final Code ERROR = new Code(6, "ERROR");
    //public static final Code PLAN_CREATE_FAILURE = new Code(7);
    public static final Code LOCK_UNAVAILABLE = new Code(8, "LOCK_UNAVAILABLE");
    public static final Code API_SERVER_ERROR = new Code(9, "API_SERVER_ERROR");
    //public static final Code SCHEDULER_BUILD_FAILED = new Code(10);
    public static final Code SCHEDULER_ALREADY_UNINSTALLING = new Code(11, "SCHEDULER_ALREADY_UNINSTALLING");
    //public static final Code SCHEDULER_INITIALIZATION_FAILURE = new Code(12);
    public static final Code DRIVER_EXITED = new Code(13, "DRIVER_EXITED");

    /**
     * Immediately exits the process with the ordinal value of the provided {@link ProcessExit}.
     */
    @SuppressWarnings("DM_EXIT")
    public static void exit(Code code) {
        String message = String.format("Process exiting immediately with code: %s[%d]", code, code.getValue());
        System.err.println(message);
        System.out.println(message);
        System.err.println("Printing final thread state...");
        new ThreadDump(ManagementFactory.getThreadMXBean()).dump(System.err);
        System.exit(code.getValue());
    }

    /**
     * Similar to {@link #exit(ProcessExit)}, except also prints the stack trace of the provided exception before
     * exiting the process. This may be used in contexts where the process is exiting in response to a thrown exception.
     */
    public static void exit(Code code, Throwable e) {
        e.printStackTrace(System.err);
        e.printStackTrace(System.out);
        exit(code);
    }

    /**
     * A reason for the Scheduler process to exit.
     */
    public static class Code {
        private final int value;
        private final String name;

        private Code(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
