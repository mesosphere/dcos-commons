package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.config.DefaultTaskConfigRouter;
import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.TaskTypeRule;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * HDFS Service.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            // We manually configure the pods to have additional tasktype placement rules as required for HDFS:
            new DefaultService(getBuilder(YAMLServiceSpecFactory.generateRawSpecFromYAML(new File(args[0])))).run();
        } else {
            LOGGER.error("Missing file argument");
            System.exit(1);
        }
    }

    private static DefaultScheduler.Builder getBuilder(RawServiceSpec rawServiceSpec)
            throws Exception {
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, schedulerFlags);
        DefaultScheduler.Builder builder = DefaultScheduler
                .newBuilder(serviceSpecWithCustomizedPods(serviceSpec), schedulerFlags)
                .setRecoveryManagerFactory(new HdfsRecoveryPlanOverriderFactory())
                .setPlansFrom(rawServiceSpec);
        return builder
                .setEndpointProducer("hdfs-site.xml", EndpointProducer.constant(getHdfsSiteXml()))
                .setEndpointProducer("core-site.xml", EndpointProducer.constant(getCoreSiteXml()));
    }

    private static String getHdfsSiteXml() {
        return renderTemplate(System.getProperty("user.dir") + "/hdfs-scheduler/hdfs-site.xml");
    }

    private static String getCoreSiteXml() {
        return renderTemplate(System.getProperty("user.dir") + "/hdfs-scheduler/core-site.xml");
    }

    private static String renderTemplate(String pathStr) {
        Path path = Paths.get(pathStr);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            String error = String.format("Failed to render %s", pathStr);
            LOGGER.error(error, e);
            return error;
        }

        // Simulate the envvars that would be passed to a task. We want to render the config as though it was being done
        // in a task. Manually copy over FRAMEWORK_NAME which is included in tasks by default.
        // TODO(nickbp): Consider switching to dedicated client xml templates instead of reusing these ones?
        Map<String, String> env = new HashMap<>(new DefaultTaskConfigRouter().getConfig("ALL").getAllEnv());
        env.put(EnvConstants.FRAMEWORK_NAME_TASKENV, System.getenv(EnvConstants.FRAMEWORK_NAME_TASKENV));

        String fileStr = new String(bytes, Charset.defaultCharset());
        return TemplateUtils.applyEnvToMustache(fileStr, env);
    }


    private static ServiceSpec serviceSpecWithCustomizedPods(DefaultServiceSpec serviceSpec) throws Exception {
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
}
