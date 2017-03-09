package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.config.DefaultTaskConfigRouter;
import com.mesosphere.sdk.offer.CommonTaskUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.evaluate.placement.AndRule;
import com.mesosphere.sdk.offer.evaluate.placement.TaskTypeRule;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HDFS Service.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            // We manually configure the pods to have additional tasktype placement rules as required for HDFS:
            new DefaultService(getBuilder(YAMLServiceSpecFactory.generateRawSpecFromYAML(new File(args[0]))));
        } else {
            LOGGER.error("Missing file argument");
            System.exit(1);
        }
    }

    private static DefaultScheduler.Builder getBuilder(RawServiceSpec rawServiceSpec)
            throws Exception {
        DefaultScheduler.Builder builder =
                DefaultScheduler.newBuilder(serviceSpecWithCustomizedPods(rawServiceSpec))
                        .setRecoveryManagerFactory(new HdfsRecoveryPlanManagerFactory())
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

        Map<String, String> env = new HashMap<>(new DefaultTaskConfigRouter().getConfig("ALL").getAllEnv());
        env.put(Constants.FRAMEWORK_NAME_TASKENV, System.getenv(Constants.FRAMEWORK_NAME_TASKENV));

        String fileStr = new String(bytes, Charset.defaultCharset());
        return CommonTaskUtils.applyEnvToMustache(fileStr, env);
    }

    private static ServiceSpec serviceSpecWithCustomizedPods(RawServiceSpec rawServiceSpec) throws Exception {
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);

        // Journal nodes avoid themselves and Name nodes.
        PodSpec journal = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "journal"))
                .placementRule(new AndRule(TaskTypeRule.avoid("journal"), TaskTypeRule.avoid("name")))
                .build();

        // Data nodes avoid themselves.
        PodSpec data = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "data"))
                .placementRule(TaskTypeRule.avoid("data"))
                .build();

        PodSpec name = getNameNodeSpec(serviceSpec);

        return DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(journal, name, data))
                .build();
    }

    private static PodSpec getNameNodeSpec(ServiceSpec serviceSpec) {
        PodSpec nameNodeSpec = getPodSpec(serviceSpec, "name");

        List<TaskSpec> zkfcTaskSpecs = nameNodeSpec.getTasks().stream()
                .filter(taskSpec -> taskSpec.getName().equals("zkfc"))
                .map(taskSpec -> DefaultTaskSpec.newBuilder(taskSpec)
                        .terminationPolicy(TerminationPolicy.DO_NOTHING)
                        .build())
                .collect(Collectors.toList());

        List<TaskSpec> nameTaskSpecs = nameNodeSpec.getTasks().stream()
                .filter(taskSpec -> !taskSpec.getName().equals("zkfc"))
                .collect(Collectors.toList());

        List<TaskSpec> taskSpecs = new ArrayList<>(nameTaskSpecs);
        taskSpecs.addAll(zkfcTaskSpecs);

        return DefaultPodSpec.newBuilder(nameNodeSpec)
                .tasks(taskSpecs)
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
