package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
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
    private static final String JOURNAL_POD_TYPE = "journal";
    private static final String NAME_POD_TYPE = "name";
    private static final String DATA_POD_TYPE = "data";
    private static final int JOURNAL_NODE_COUNT = 3;
    private static final int NAME_NODE_COUNT = 2;

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

        Map<String, String> env = System.getenv();

        String userAuthMapping;

        if (Boolean.valueOf(env.get("TASKCFG_ALL_SECURITY_KERBEROS_ENABLED"))) {
            String frameworkHostname = EndpointUtils.toAutoIpDomain(rawServiceSpec.getName(), schedulerConfig);
            userAuthMapping = new HDFSUserAuthMapperBuilder(env, frameworkHostname)
                    .addUserAuthMappingFromEnv()
                    .addDefaultUserAuthMapping(JOURNAL_POD_TYPE, "node", JOURNAL_NODE_COUNT)
                    .addDefaultUserAuthMapping(NAME_POD_TYPE, "zkfc", NAME_NODE_COUNT)
                    .addDefaultUserAuthMapping(NAME_POD_TYPE, "node", NAME_NODE_COUNT)
                    .addDefaultUserAuthMapping(DATA_POD_TYPE, "node", Integer.parseInt(env.get("DATA_COUNT")))
                    .build();
        } else {
            userAuthMapping = "";
        }



        DefaultServiceSpec serviceSpec = DefaultServiceSpec.newGenerator(rawServiceSpec, schedulerConfig, configDir)
                // Used by 'zkfc' and 'zkfc-format' tasks within this pod:
                .setPodEnv("name", SERVICE_ZK_ROOT_TASKENV, CuratorUtils.getServiceRootPath(rawServiceSpec.getName()))
                .setAllPodsEnv(HDFSAuthEnvContainer.DECODED_AUTH_TO_LOCAL, userAuthMapping)
                .build();

        return DefaultScheduler.newBuilder(setPlacementRules(serviceSpec), schedulerConfig)
                .setRecoveryManagerFactory(new HdfsRecoveryPlanOverriderFactory())
                .setPlansFrom(rawServiceSpec)
                .setEndpointProducer(HDFS_SITE_XML, EndpointProducer.constant(
                        renderTemplate(new File(configDir, HDFS_SITE_XML), serviceSpec.getName(), schedulerConfig,
                                userAuthMapping)))
                .setEndpointProducer(CORE_SITE_XML, EndpointProducer.constant(
                        renderTemplate(new File(configDir, CORE_SITE_XML), serviceSpec.getName(), schedulerConfig,
                                userAuthMapping)))
                .setCustomConfigValidators(Arrays.asList(new HDFSZoneValidator()))
                .withSingleRegionConstraint();
    }


    private static String renderTemplate(File configFile,
                                         String serviceName,
                                         SchedulerConfig schedulerConfig,
                                         String userAuthMapping) {
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
        env.put(EnvConstants.FRAMEWORK_HOST_TASKENV, EndpointUtils.toAutoIpDomain(serviceName, schedulerConfig));
        env.put(EnvConstants.FRAMEWORK_NAME_TASKENV, serviceName);
        env.put(EnvConstants.SCHEDULER_API_HOSTNAME_TASKENV,
                EndpointUtils.toSchedulerAutoIpHostname(serviceName, schedulerConfig));
        env.put(EnvConstants.SCHEDULER_API_PORT_TASKENV, String.valueOf(schedulerConfig.getApiServerPort()));
        env.put("MESOS_SANDBOX", "sandboxpath");
        env.put(SERVICE_ZK_ROOT_TASKENV, CuratorUtils.getServiceRootPath(serviceName));
        env.put(HDFSAuthEnvContainer.DECODED_AUTH_TO_LOCAL, userAuthMapping);

        String fileStr = new String(bytes, StandardCharsets.UTF_8);
        return TemplateUtils.renderMustacheThrowIfMissing(configFile.getName(), fileStr, env);
    }

    private static ServiceSpec setPlacementRules(DefaultServiceSpec serviceSpec) {
        PodSpec journal = getJournalPodSpec(serviceSpec);
        PodSpec name = getNamePodSpec(serviceSpec);
        PodSpec data = getDataPodSpec(serviceSpec);

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

    private static PodSpec getJournalPodSpec(ServiceSpec serviceSpec) {
        // Journal nodes avoid themselves and Name nodes.
        PlacementRule placementRule = new AndRule(
                TaskTypeRule.avoid(JOURNAL_POD_TYPE), TaskTypeRule.avoid(NAME_POD_TYPE));
        return getPodPlacementRule(serviceSpec, JOURNAL_POD_TYPE, placementRule);
    }

    private static PodSpec getNamePodSpec(ServiceSpec serviceSpec) {
        // Name nodes avoid themselves and journal nodes.
        PlacementRule placementRule = new AndRule(
                TaskTypeRule.avoid(NAME_POD_TYPE),
                TaskTypeRule.avoid(JOURNAL_POD_TYPE)
        );
        return getPodPlacementRule(serviceSpec, NAME_POD_TYPE, placementRule);
    }

    private static PodSpec getDataPodSpec(ServiceSpec serviceSpec) {
        // Data nodes avoid themselves.
        PlacementRule placementRule = TaskTypeRule.avoid(DATA_POD_TYPE);
        return getPodPlacementRule(serviceSpec, DATA_POD_TYPE, placementRule);
    }

    private static PodSpec getPodPlacementRule(ServiceSpec serviceSpec, String podType, PlacementRule placementRule) {
        if (getPodSpec(serviceSpec, podType).getPlacementRule().isPresent()) {
            placementRule = new AndRule(
                    placementRule,
                    getPodSpec(serviceSpec, podType).getPlacementRule().get()
            );
        }

        return DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, podType))
                .placementRule(placementRule)
                .build();

    }
}
