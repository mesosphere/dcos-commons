package com.mesosphere.sdk.specification.yaml;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.io.FileUtils;

import com.mesosphere.sdk.config.DefaultTaskEnvRouter;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Generates a {@link ServiceSpec} from a given YAML definition in the form of a {@link RawServiceSpec}.
 */
public class DefaultServiceSpecBuilder {

    /**
     * Implementation for reading files from disk. Meant to be overridden by a mock in tests.
     */
    @VisibleForTesting
    public static class FileReader {
        public String read(String path) throws IOException {
            return FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
        }
    }

    private final RawServiceSpec rawServiceSpec;
    private final SchedulerFlags schedulerFlags;
    private final DefaultTaskEnvRouter taskEnvRouter;
    private FileReader fileReader;

    public DefaultServiceSpecBuilder(RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags) {
        this(rawServiceSpec, schedulerFlags, new DefaultTaskEnvRouter());
    }

    @VisibleForTesting
    public DefaultServiceSpecBuilder(
            RawServiceSpec rawServiceSpec, SchedulerFlags schedulerFlags, DefaultTaskEnvRouter taskEnvRouter) {
        this.rawServiceSpec = rawServiceSpec;
        this.schedulerFlags = schedulerFlags;
        this.taskEnvRouter = taskEnvRouter;
        this.fileReader = new FileReader();
    }

    /**
     * Assigns an environment variable to be included in all service tasks. Note that this may be overridden via
     * {@code TASKCFG_*} scheduler environment variables at runtime, and by pod-specific settings provided via
     * {@link #setPodTaskEnv(String, String)}.
     */
    public DefaultServiceSpecBuilder setGlobalTaskEnv(String key, String value) {
        this.taskEnvRouter.setGlobalTaskEnv(key, value);
        return this;
    }

    /**
     * Assigns an environment variable to be included in tasks for the specified pod type. For example, all tasks
     * running inside of "index" pods. Note that this may be overridden via {@code TASKCFG_*} scheduler environment
     * variables at runtime.
     */
    public DefaultServiceSpecBuilder setPodTaskEnv(String podType, String key, String value) {
        this.taskEnvRouter.setPodTaskEnv(podType, key, value);
        return this;
    }

    /**
     * Assigns a custom {@link FileReader} implementation for reading config file templates. This is exposed to support
     * mockery in tests.
     */
    @VisibleForTesting
    public DefaultServiceSpecBuilder setFileReader(FileReader fileReader) {
        this.fileReader = fileReader;
        return this;
    }

    public DefaultServiceSpec build() throws Exception {
        return YAMLToInternalMappers.from(rawServiceSpec, schedulerFlags, taskEnvRouter, fileReader);
    }
}
