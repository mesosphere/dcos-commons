package com.mesosphere.sdk.offer;

/**
 * This class encapsulates constants of relevance to the SDK.
 */
public class Constants {
    public static final String COMMAND_DATA_PACKAGE_EXECUTOR = "command_data_package_executor";
    public static final String TASK_NAME_DELIM = "__";

    public static final String CONFIG_TEMPLATE_KEY_PREFIX = "config_template:";
    public static final String GOAL_STATE_KEY = "goal_state";
    public static final String POD_INSTANCE_INDEX_KEY = "POD_INSTANCE_INDEX";
    public static final String PORT_NAME_LABEL_PREFIX = "port_";
    public static final String TARGET_CONFIGURATION_KEY = "target_configuration";
    public static final String FRAMEWORK_NAME_KEY = "FRAMEWORK_NAME";
    public static final String TASK_NAME_KEY = "TASK_NAME";
    public static final String TRANSIENT_FLAG_KEY = "transient";

    public static final String EXECUTOR_URI = "EXECUTOR_URI";
    public static final String LIBMESOS_URI = "LIBMESOS_URI";
    public static final String JAVA_URI = "JAVA_URI";
    public static final String DEFAULT_JAVA_URI = "https://downloads.mesosphere.com/java/jre-8u112-linux-x64.tar.gz";

    public static final String DEPLOY_PLAN_NAME = "deploy";
    public static final String PORTS_RESOURCE_TYPE = "ports";
    public static final String DISK_RESOURCE_TYPE = "disk";
}
