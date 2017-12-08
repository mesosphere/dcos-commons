package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.TaskTypeRule;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.SchedulerRunner;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Main entry point for the Scheduler.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String AUTH_TO_LOCAL = "AUTH_TO_LOCAL";
    private static final String DECODED_AUTH_TO_LOCAL = "DECODED_" + AUTH_TO_LOCAL;
    private static final String TASKCFG_ALL_AUTH_TO_LOCAL = TaskEnvRouter.TASKCFG_GLOBAL_ENV_PREFIX + AUTH_TO_LOCAL;

    static final String SERVICE_ZK_ROOT_TASKENV = "SERVICE_ZK_ROOT";
    static final String HDFS_SITE_XML = "hdfs-site.xml";
    static final String CORE_SITE_XML = "core-site.xml";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one file argument, got: " + Arrays.toString(args));
        }
        SchedulerRunner
                .fromSchedulerBuilder(createSchedulerBuilder(new File(args[0])))
                .run();
    }

    private static SchedulerBuilder createSchedulerBuilder(File yamlSpecFile) throws Exception {
        RawServiceSpec rawServiceSpec = RawServiceSpec.newBuilder(yamlSpecFile).build();
        File configDir = yamlSpecFile.getParentFile();
        SchedulerConfig schedulerConfig = SchedulerConfig.fromEnv();
        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, configDir)
                // Used by 'zkfc' and 'zkfc-format' tasks within this pod:
                .setPodEnv("name", SERVICE_ZK_ROOT_TASKENV, CuratorUtils.getServiceRootPath(rawServiceSpec.getName()))
                .setAllPodsEnv(DECODED_AUTH_TO_LOCAL,
                                getHDFSUserAuthMappings(System.getenv(), TASKCFG_ALL_AUTH_TO_LOCAL))
                .build();

        return DefaultScheduler.newBuilder(setPlacementRules(serviceSpec), schedulerConfig)
                .setRecoveryManagerFactory(new HdfsRecoveryPlanOverriderFactory())
                .setPlansFrom(rawServiceSpec)
                .setEndpointProducer(HDFS_SITE_XML, EndpointProducer.constant(
                        renderTemplate(new File(configDir, HDFS_SITE_XML), serviceSpec.getName())))
                .setEndpointProducer(CORE_SITE_XML, EndpointProducer.constant(
                        renderTemplate(new File(configDir, CORE_SITE_XML), serviceSpec.getName())));
    }

    private static String renderTemplate(File configFile, String serviceName) throws Exception {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(configFile.toPath());
        } catch (IOException e) {
            String error = String.format("Failed to read %s", configFile.getAbsolutePath());
            LOGGER.error(error, e);
            return error;
        }

        // Simulate the envvars that would be passed to a task. We want to render the config as though it was being done
        // in a task. Manually copy over a couple envvars which is included in tasks by default.
        Map<String, String> env = new HashMap<>(new TaskEnvRouter().getConfig("ALL"));
        env.put(EnvConstants.FRAMEWORK_HOST_TASKENV, EndpointUtils.toAutoIpDomain(serviceName));
        env.put(EnvConstants.FRAMEWORK_NAME_TASKENV, serviceName);
        env.put("MESOS_SANDBOX", "sandboxpath");
        env.put(SERVICE_ZK_ROOT_TASKENV, CuratorUtils.getServiceRootPath(serviceName));
        env.put(DECODED_AUTH_TO_LOCAL, getHDFSUserAuthMappings(env, AUTH_TO_LOCAL));

        String fileStr = new String(bytes, StandardCharsets.UTF_8);
        return TemplateUtils.renderMustacheThrowIfMissing(configFile.getName(), fileStr, env);
    }

    private static ServiceSpec setPlacementRules(DefaultServiceSpec serviceSpec) throws Exception {
        // Journal nodes avoid themselves and Name nodes.
        PodSpec journal = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "journal"))
                .placementRule(new AndRule(TaskTypeRule.avoid("journal"), TaskTypeRule.avoid("name")))
                .build();

        // Name nodes avoid themselves and journal nodes.
        PodSpec name = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "name"))
                .placementRule(new AndRule(TaskTypeRule.avoid("name"), TaskTypeRule.avoid("journal")))
                .build();

        // Data nodes avoid themselves.
        PodSpec data = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "data"))
                .placementRule(TaskTypeRule.avoid("data"))
                .build();

        return DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(journal, name, data))
                .build();
    }

    private static PodSpec getPodSpec(ServiceSpec serviceSpec, String podName) {
        Optional<PodSpec> match = serviceSpec.getPods().stream()
                .filter(podSpec -> podSpec.getType().equals(podName))
                .findFirst();
        if (!match.isPresent()) {
            throw new IllegalStateException(String.format(
                    "Missing required pod named '%s' in service spec", podName));
        }
        return match.get();
    }

    private static String getHDFSUserAuthMappings(Map<String, String> env, String envVarKeyName) throws Exception {
        Base64.Decoder decoder = Base64.getDecoder();
        String base64Mappings = env.get(envVarKeyName);
        byte[] hdfsUserAuthMappingsBytes = decoder.decode(base64Mappings);
        String authMappings = new String(hdfsUserAuthMappingsBytes, "UTF-8");
        return authMappings;
    }
}
