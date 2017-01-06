package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.offer.constrain.AndRule;
import com.mesosphere.sdk.offer.constrain.TaskTypeRule;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.RawServiceSpecification;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

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

    private static DefaultScheduler.Builder getBuilder(RawServiceSpecification rawServiceSpecification)
            throws Exception {
        DefaultScheduler.Builder builder =
                DefaultScheduler.newBuilder(serviceSpecWithCustomizedPods(rawServiceSpecification))
                .setPlansFrom(rawServiceSpecification);
        // TODO(nick): The endpointproducers should produce valid HDFS xml files. They can get the info they need from
        // scheduler envvars and/or the ServiceSpec. If they need current task state, they could be passed the
        // StateStore from builder.getStateStore() when they're constructed, which they could then access to get current
        // task state when EndpointProducer.getEndpoint() is called.
        return builder
                .setEndpointProducer("hdfs-site.xml", EndpointProducer.constant("TODO: hdfs-site.xml content"))
                .setEndpointProducer("core-site.xml", EndpointProducer.constant("TODO: core-site.xml content"));
    }

    private static ServiceSpec serviceSpecWithCustomizedPods(RawServiceSpecification rawServiceSpecification)
            throws Exception {
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

        return DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(journal, name, zkfc, data))
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
