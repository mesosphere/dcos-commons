package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.api.types.EndpointProducer;
import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.kafka.api.BrokerResource;
import com.mesosphere.sdk.kafka.api.KafkaZKClient;
import com.mesosphere.sdk.kafka.api.TopicResource;
import com.mesosphere.sdk.kafka.cmd.CmdExecutor;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Kafka Service.
 */
public class KafkaService extends DefaultService {
    public KafkaService(File pathToYamlSpecification) throws Exception {
        super(createSchedulerBuilder(pathToYamlSpecification));
    }

    private static DefaultScheduler.Builder createSchedulerBuilder(File pathToYamlSpecification)
            throws Exception {

        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification);
        SchedulerFlags schedulerFlags = SchedulerFlags.fromEnv();
        DefaultScheduler.Builder schedulerBuilder = DefaultScheduler.newBuilder(
                YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec, schedulerFlags), schedulerFlags)
                .setPlansFrom(rawServiceSpec);

        schedulerBuilder.setEndpointProducer("zookeeper", EndpointProducer.constant(
                schedulerBuilder.getServiceSpec().getZookeeperConnection() +
                        DcosConstants.SERVICE_ROOT_PATH_PREFIX + schedulerBuilder.getServiceSpec().getName()));

        schedulerBuilder.setCustomResources(
                getResources(
                        schedulerBuilder.getServiceSpec().getZookeeperConnection(),
                        schedulerBuilder.getServiceSpec().getName()));
        return schedulerBuilder;
    }

    private static Collection<Object> getResources(String zookeeperConnection, String serviceName) {
        KafkaZKClient kafkaZKClient = new KafkaZKClient(
                zookeeperConnection,
                DcosConstants.SERVICE_ROOT_PATH_PREFIX + serviceName);

        final Collection<Object> apiResources = new ArrayList<>();
        apiResources.add(new BrokerResource(kafkaZKClient));
        apiResources.add(new TopicResource(
                new CmdExecutor(kafkaZKClient, System.getenv("KAFKA_VERSION_PATH")),
                kafkaZKClient));

        return apiResources;
    }
}
