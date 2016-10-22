package org.apache.mesos.specification;

/**
 * Represents an optionally templated configuration file to be written before the task is started.
 */
public interface ConfigFileSpecification {
    /**
     * Path where this file will be written, relative to the initial working directory of the task.
     */
    String getRelativePath();

    /**
     * Content of the template which will be rendered and written to the above path. Any template
     * parameters in the returned content must map to environment variables at the Executor.
     *
     * Content may either be static, or may follow the Moustache template format, where all
     * parameters must map to environment variables present in the task's environment.
     *
     * This content MUST be parseable by Protobuf as a string type -- arbitrary binary data is not
     * supported. Binaries should instead be passed as resource files.
     *
     * Example input:
     * - Task env: {"FOO":"BAR", "BAR":"BAZ"}
     * - Template content: "# config.ini\nFOO={{FOO}}\nBAR={{BAR}}"
     * Example output:
     *   # config.ini
     *   FOO=BAR
     *   BAR=BAZ
     */
    String getTemplateContent();
}
