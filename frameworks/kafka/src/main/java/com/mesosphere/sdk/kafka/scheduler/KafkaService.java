package com.mesosphere.sdk.kafka.scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.kafka.api.BrokerController;
import com.mesosphere.sdk.scheduler.SchedulerDriverFactory;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.*;

/**
 * Kafka Service.
 */
public class KafkaService extends  DefaultService {
    protected static final int FAILOVER_TIMEOUT_SEC = 2 * 7 * 24 * 60 * 60;
    protected static final int LOCK_ATTEMPTS = 3;
    protected static final String USER = "root";
    protected static final String LOCK_PATH = "lock";

    protected static final Logger LOGGER = LoggerFactory.getLogger(KafkaService.class);

    public KafkaService(File pathToYamlSpecification) throws Exception {
        super();

        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification);

        DefaultScheduler.Builder schedulerBuilder =
                DefaultScheduler.newBuilder(YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec));

        schedulerBuilder.setPlansFrom(rawServiceSpec);

        CuratorFramework curatorClient = CuratorFrameworkFactory.newClient(
                schedulerBuilder.getServiceSpec().getZookeeperConnection(), CuratorUtils.getDefaultRetry());
        curatorClient.start();

        InterProcessMutex curatorMutex = super.lock(curatorClient, schedulerBuilder.getServiceSpec().getName(),
                LOCK_PATH, LOCK_ATTEMPTS);
        try {
            registerAndRun(schedulerBuilder);
        } finally {
            super.unlock(curatorMutex);
            curatorClient.close();
        }
    }

    public void registerAndRun(DefaultScheduler.Builder schedulerBuilder){
        ServiceSpec serviceSpec = schedulerBuilder.getServiceSpec();
        DefaultScheduler scheduler = schedulerBuilder.build();

        String zookeeperConnection = serviceSpec.getZookeeperConnection();
        Collection<Object> jsonResources = new ArrayList<>();

        //TODO: do error handling if env var does not exist
        jsonResources.add(new BrokerController(System.getenv("TASKCFG_ALL_KAFKA_ZOOKEEPER_URI")));
        startApiServer(scheduler,  serviceSpec.getApiPort(), jsonResources);

        Protos.FrameworkInfo frameworkInfo =
                super.getFrameworkInfo(serviceSpec, schedulerBuilder.getStateStore(), USER, FAILOVER_TIMEOUT_SEC);

        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        new SchedulerDriverFactory().create(scheduler,
                    frameworkInfo,
                    String.format("zk://%s/mesos", zookeeperConnection)).run();
    }
}
