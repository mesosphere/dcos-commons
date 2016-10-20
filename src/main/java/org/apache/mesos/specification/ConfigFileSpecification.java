package org.apache.mesos.specification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Represents an optionally templated configuration file to be written before the task is started.
 */
@JsonDeserialize(as=DefaultConfigFileSpecification.class)
public interface ConfigFileSpecification {
    /**
     * Path where this file will be written, relative to the initial working directory of the task.
     */
    @JsonProperty("relative_path")
    String getRelativePath();

    /**
     * Content of the template which will be rendered and written to the above path. Any template
     * parameters in the returned content must map to environment variables at the Executor.
     *
     * Content may either be static, or may follow the Moustache template format, where all
     * parameters must map to environment variables present in the task's environment.
     *
     * Example input:
     * - Task env: {"FOO":"BAR", "BAR":"BAZ"}
     * - Template content: "# config.ini\nFOO={{FOO}}\nBAR={{BAR}}"
     * Example output:
     *   # config.ini
     *   FOO=BAR
     *   BAR=BAZ
     */
    @JsonProperty("template_content")
    String getTemplateContent();
}
