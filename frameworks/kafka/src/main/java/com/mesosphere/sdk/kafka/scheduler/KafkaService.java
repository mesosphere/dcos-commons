package com.mesosphere.sdk.kafka.scheduler;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.kafka.api.BrokerController;
import com.mesosphere.sdk.kafka.api.InterruptProceed;
import com.mesosphere.sdk.scheduler.SchedulerDriverFactory;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.state.StateStore;
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
    protected static final int failoverTimeoutSec = 2 * 7 * 24 * 60 * 60;
    protected static final int lockAttempts = 3;
    protected static final String userString = "root";
    protected static final String lockPath = "lock";

    protected static final Logger LOGGER = LoggerFactory.getLogger(KafkaService.class);

    public KafkaService(File pathToYamlSpecification) throws Exception {

        //TODO(Mehmet):  call DefaultService(DefaultScheduler.Builder schedulerBuilder) and override register
        super();

        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(pathToYamlSpecification);

        DefaultScheduler.Builder schedulerBuilder =
                DefaultScheduler.newBuilder(YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec));

        schedulerBuilder.setPlansFrom(rawServiceSpec);

        CuratorFramework curatorClient = CuratorFrameworkFactory.newClient(
                schedulerBuilder.getServiceSpec().getZookeeperConnection(), CuratorUtils.getDefaultRetry());
        curatorClient.start();

        InterProcessMutex curatorMutex = super.lock(curatorClient, schedulerBuilder.getServiceSpec().getName(),
                lockPath, lockAttempts);
        try {
            registerAndRun(schedulerBuilder);
        } finally {
            super.unlock(curatorMutex);
            curatorClient.close();
        }

    }

    public void registerAndRun(DefaultScheduler.Builder schedulerBuilder){

        StateStore stateStore = schedulerBuilder.getStateStore();
        DefaultScheduler scheduler = schedulerBuilder.build();

        ServiceSpec serviceSpec = schedulerBuilder.getServiceSpec();

        String zookeeperConnection = serviceSpec.getZookeeperConnection();

        Protos.FrameworkInfo frameworkInfo =
                super.getFrameworkInfo(serviceSpec, schedulerBuilder.getStateStore(), userString, failoverTimeoutSec);

        Collection<Object> jsonResources = new ArrayList<>();

        LOGGER.info("Registering framework: {}", TextFormat.shortDebugString(frameworkInfo));
        new SchedulerDriverFactory().create(scheduler,
                    frameworkInfo,
                    String.format("zk://%s/mesos", zookeeperConnection)).run();


        jsonResources.add(new InterruptProceed(scheduler.getPlanManager()));

        jsonResources.add(new BrokerController(stateStore, scheduler.getTaskKiller(),
                System.getenv("KAFKA_ZOOKEEPER_URI")));

        startApiServer(scheduler,  serviceSpec.getApiPort(), jsonResources);

    }
}
