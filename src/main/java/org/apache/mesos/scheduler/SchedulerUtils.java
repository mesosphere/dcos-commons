package org.apache.mesos.scheduler;

/**
 * Created by gabriel on 8/29/16.
 */
public class SchedulerUtils {
    public static String nameToRole(String frameworkName) {
        return frameworkName + "-role";
    }

    public static String nameToPrincipal(String frameworkName) {
        return frameworkName + "-principal";
    }
}
