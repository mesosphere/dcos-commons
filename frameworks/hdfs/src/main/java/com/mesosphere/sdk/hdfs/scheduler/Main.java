package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.offer.constrain.AndRule;
import com.mesosphere.sdk.offer.constrain.TaskTypeRule;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpecification;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;

/**
 * HDFS Service.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        try {
            if (args.length > 0) {
                new HdfsService(new File(args[0]));
            }
        } catch (Throwable t) {
            LOGGER.error("Initial startup failed.", t);
        }
    }

    private static class HdfsService extends DefaultService {
        public HdfsService(File yamlFile) throws Exception {
            RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(yamlFile);
            DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);

            // Journal nodes avoid themselves and Name nodes.
            PodSpec journal = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "journal"))
                    .placementRule(new AndRule(TaskTypeRule.avoid("journal"), TaskTypeRule.avoid("name")))
                    .build();

            // Name nodes avoid themselves and journal nodes.
            PodSpec name = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "name"))
                    .placementRule(new AndRule(TaskTypeRule.avoid("name"), TaskTypeRule.avoid("journal")))
                    .build();

            // ZKFC nodes avoid themselves and colocate with name nodes.
            PodSpec zkfc = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "zkfc"))
                    .placementRule(new AndRule(TaskTypeRule.avoid("zkfc"), TaskTypeRule.colocateWith("name")))
                    .build();

            // Data nodes avoid themselves.
            PodSpec data = DefaultPodSpec.newBuilder(getPodSpec(serviceSpec, "data"))
                    .placementRule(TaskTypeRule.avoid("data"))
                    .build();

            serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                    .pods(Arrays.asList(journal, name, zkfc, data))
                    .build();

            super.serviceSpec = serviceSpec;
            init();
            super.plans = generatePlansFromRawSpec(rawServiceSpecification);
            register(super.serviceSpec, super.plans);
        }

    }

    private static PodSpec getPodSpec(ServiceSpec serviceSpec, String podName) {
        return serviceSpec.getPods().stream()
                .filter(podSpec -> podSpec.getType().equals(podName))
                .findFirst()
                .get();
    }
}
