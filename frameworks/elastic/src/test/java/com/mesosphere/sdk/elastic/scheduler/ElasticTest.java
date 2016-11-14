package com.mesosphere.sdk.elastic.scheduler;

import com.google.protobuf.TextFormat;
import de.bechte.junit.runners.context.HierarchicalContextRunner;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TaskSpecification;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(HierarchicalContextRunner.class)
public class ElasticTest {
    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();
    private Elastic elastic;

    @Before
    public void before() throws Exception {
        environmentVariables.set("DNS_UTILS_URI", "https://s3-us-west-1.amazonaws.com/hello-universe-assets/dns_utils.tar.gz");
        environmentVariables.set("JAVA_URI", "https://s3-us-west-2.amazonaws.com/infinity-artifacts/kafka/jre-8u91-linux-x64.tar.gz");
        environmentVariables.set("ELASTICSEARCH_VER_NAME", "elasticsearch-5.0.0-beta1");
        environmentVariables.set("KIBANA_VER_NAME", "https://artifacts.elastic.co/downloads/kibana/kibana-5.0.0-beta1-linux-x86_64.tar.gz");
        environmentVariables.set("ELASTICSEARCH_URI", "https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.0.0-beta1.tar.gz");
        environmentVariables.set("XPACK_URI", "https://artifacts.elastic.co/downloads/packs/x-pack/x-pack-5.0.0-beta1.zip");
        environmentVariables.set("KIBANA_URI", "https://artifacts.elastic.co/downloads/kibana/kibana-5.0.0-beta1-linux-x86_64.tar.gz");
        environmentVariables.set("DIAGNOSTICS_URI", "https://github.com/elastic/elasticsearch-support-diagnostics/releases/download/2.1.2/support-diagnostics-2.1.2-dist.zip");
        environmentVariables.set("STATSD_PLUGIN_URI", "https://s3-us-west-1.amazonaws.com/hello-universe-assets/elasticsearch-statsd-5.0.0-beta1-SNAPSHOT.zip");
        environmentVariables.set("ELASTICSEARCH_PLUGINS", "plugin-1, plugin-2");
        environmentVariables.set("SERVICE_NAME", "elastic-framework");
        environmentVariables.set("MESOS_FRAMEWORK_ID", "elastic-framework-id");
        environmentVariables.set("FRAMEWORK_USER", "core");
        environmentVariables.set("MASTER_NODE_CPUS", "1.0");
        environmentVariables.set("MASTER_NODE_MEM", "2048");
        environmentVariables.set("MASTER_NODE_HEAP_MB", "1024");
        environmentVariables.set("MASTER_NODE_DISK", "5000");
        environmentVariables.set("MASTER_NODE_MOUNTED_DISK", "false");
        environmentVariables.set("MASTER_NODE_HTTP_PORT", "9250");
        environmentVariables.set("MASTER_NODE_TRANSPORT_PORT", "9350");
        environmentVariables.set("DATA_NODE_COUNT", "2");
        environmentVariables.set("DATA_NODE_CPUS", "2.0");
        environmentVariables.set("DATA_NODE_MEM", "2049");
        environmentVariables.set("DATA_NODE_HEAP_MB", "1025");
        environmentVariables.set("DATA_NODE_DISK", "5002");
        environmentVariables.set("DATA_NODE_MOUNTED_DISK", "false");
        environmentVariables.set("DATA_NODE_HTTP_PORT", "9260");
        environmentVariables.set("DATA_NODE_TRANSPORT_PORT", "9360");
        environmentVariables.set("INGEST_NODE_COUNT", "1");
        environmentVariables.set("INGEST_NODE_CPUS", "2.1");
        environmentVariables.set("INGEST_NODE_MEM", "2050");
        environmentVariables.set("INGEST_NODE_HEAP_MB", "1026");
        environmentVariables.set("INGEST_NODE_DISK", "5003");
        environmentVariables.set("INGEST_NODE_MOUNTED_DISK", "false");
        environmentVariables.set("INGEST_NODE_HTTP_PORT", "9270");
        environmentVariables.set("INGEST_NODE_TRANSPORT_PORT", "9370");
        environmentVariables.set("COORDINATOR_NODE_COUNT", "1");
        environmentVariables.set("COORDINATOR_NODE_CPUS", "1.2");
        environmentVariables.set("COORDINATOR_NODE_MEM", "2051");
        environmentVariables.set("COORDINATOR_NODE_HEAP_MB", "1027");
        environmentVariables.set("COORDINATOR_NODE_DISK", "5004");
        environmentVariables.set("COORDINATOR_NODE_MOUNTED_DISK", "false");
        environmentVariables.set("COORDINATOR_NODE_HTTP_PORT", "9280");
        environmentVariables.set("COORDINATOR_NODE_TRANSPORT_PORT", "9380");
        environmentVariables.set("KIBANA_COUNT", "1");
        environmentVariables.set("KIBANA_CPUS", "1.0");
        environmentVariables.set("KIBANA_MEM", "768");
        environmentVariables.set("KIBANA_DISK", "100");
        environmentVariables.set("KIBANA_NODE_MOUNTED_DISK", "false");
        environmentVariables.set("KIBANA_PORT", "5601");
        environmentVariables.set("KIBANA_PASSWORD", "secret");
        environmentVariables.set("STATSD_UDP_PORT", "10001");
        environmentVariables.set("STATSD_UDP_HOST", "udp_host");
        environmentVariables.set("EXECUTOR_URI", "https://downloads.mesosphere.com/dcos-commons/artifacts/executor.zip");
    }


    public class MasterTransportPortZero {
        @Before
        public void zeroPort() throws Exception {
            environmentVariables.set("MASTER_NODE_HTTP_PORT", "0");
            environmentVariables.set("MASTER_NODE_TRANSPORT_PORT", "0");
            elastic = new Elastic();
        }

        @Test
        public void getTaskSets() throws Exception {
            TaskSet masterTaskSet = elastic.getServiceSpecification().getTaskSets().get(1);
            Assert.assertEquals("master", masterTaskSet.getName());
            List<TaskSpecification> masterTaskSpecifications = masterTaskSet.getTaskSpecifications();
            TaskSpecification masterTaskSpecification = masterTaskSpecifications.get(0);
            Assert.assertEquals("master-0", masterTaskSpecification.getName());
        }

    }

    public class NoCoordinatorNodes {
        @Before
        public void noCoordinatorNodes() throws Exception {
            environmentVariables.set("COORDINATOR_NODE_COUNT", "0");
            elastic = new Elastic();
        }

        @Test
        public void getTaskSets() throws Exception {
            Assert.assertEquals(4, elastic.getServiceSpecification().getTaskSets().size());
            List<String> taskNames = elastic.getServiceSpecification().getTaskSets().stream()
                .map(TaskSet::getName).collect(Collectors.toList());
            Assert.assertEquals(Arrays.asList("kibana", "master", "data", "ingest"), taskNames);
        }

    }

    public class NoKibanaNodes {
        @Before
        public void noKibanaNodes() throws Exception {
            environmentVariables.set("KIBANA_COUNT", "0");
            elastic = new Elastic();
        }

        @Test
        public void getTaskSets() throws Exception {
            Assert.assertEquals(4, elastic.getServiceSpecification().getTaskSets().size());
            List<String> taskNames = elastic.getServiceSpecification().getTaskSets().stream()
                .map(TaskSet::getName).collect(Collectors.toList());
            Assert.assertEquals(Arrays.asList("master", "data", "ingest", "coordinator"), taskNames);
        }

    }

    public class FullySpecified {
        @Before
        public void fullySpecify() throws Exception {
        }

        @Test
        public void getName() throws Exception {
            elastic = new Elastic();
            Assert.assertEquals("elastic-framework", elastic.getServiceSpecification().getName());
        }

        @Test
        public void getTaskSets() throws Exception {
            elastic = new Elastic();
            Assert.assertEquals(5, elastic.getServiceSpecification().getTaskSets().size());

            TaskSet kibanaTaskSet = elastic.getServiceSpecification().getTaskSets().get(0);
            Assert.assertEquals("kibana", kibanaTaskSet.getName());
            List<TaskSpecification> kibanaTaskSpecifications = kibanaTaskSet.getTaskSpecifications();
            Assert.assertEquals(1, kibanaTaskSpecifications.size());
            TaskSpecification kibanaTaskSpecification = kibanaTaskSpecifications.get(0);
            Assert.assertEquals("kibana-0", kibanaTaskSpecification.getName());
            //noinspection OptionalGetWithoutIsPresent
            Assert.assertEquals("uris { value: \"https://artifacts.elastic.co/downloads/kibana/kibana-5.0.0-beta1-linux-x86_64.tar.gz\" } uris { value: \"https://artifacts.elastic.co/downloads/packs/x-pack/x-pack-5.0.0-beta1.zip\" } environment { variables { name: \"KIBANA_ENCRYPTION_KEY\" value: \"elastic-framework-id\" } variables { name: \"KIBANA_PORT\" value: \"5601\" } variables { name: \"KIBANA_ELASTICSEARCH_URL\" value: \"http://master-0.elastic-framework.mesos:9250\" } variables { name: \"KIBANA_SERVER_NAME\" value: \"kibana-0\" } variables { name: \"KIBANA_PASSWORD\" value: \"secret\" } } value: \"$MESOS_SANDBOX/https://artifacts.elastic.co/downloads/kibana/kibana-5.0.0-beta1-linux-x86_64.tar.gz/bin/kibana-plugin install file://$MESOS_SANDBOX/x-pack-5.0.0-beta1.zip && exec $MESOS_SANDBOX/https://artifacts.elastic.co/downloads/kibana/kibana-5.0.0-beta1-linux-x86_64.tar.gz/bin/kibana -c kibana.yml\" user: \"core\"",
                TextFormat.shortDebugString(kibanaTaskSpecification.getCommand().get()));

            TaskSet masterTaskSet = elastic.getServiceSpecification().getTaskSets().get(1);
            Assert.assertEquals("master", masterTaskSet.getName());
            List<TaskSpecification> masterTaskSpecifications = masterTaskSet.getTaskSpecifications();
            Assert.assertEquals(3, masterTaskSpecifications.size());
            TaskSpecification masterTaskSpecification = masterTaskSpecifications.get(0);
            Assert.assertEquals("master-0", masterTaskSpecification.getName());

            TaskSet dataTaskSet = elastic.getServiceSpecification().getTaskSets().get(2);
            Assert.assertEquals("data", dataTaskSet.getName());
            List<TaskSpecification> dataTaskSpecifications = dataTaskSet.getTaskSpecifications();
            Assert.assertEquals(2, dataTaskSpecifications.size());
            TaskSpecification dataTaskSpecification = dataTaskSpecifications.get(1);
            Assert.assertEquals("data-1", dataTaskSpecification.getName());

            TaskSet ingestTaskSet = elastic.getServiceSpecification().getTaskSets().get(3);
            Assert.assertEquals("ingest", ingestTaskSet.getName());
            List<TaskSpecification> ingestTaskSpecifications = ingestTaskSet.getTaskSpecifications();
            Assert.assertEquals(1, ingestTaskSpecifications.size());
            TaskSpecification ingestTaskSpecification = ingestTaskSpecifications.get(0);
            Assert.assertEquals("ingest-0", ingestTaskSpecification.getName());

            TaskSet coordinatorTaskSet = elastic.getServiceSpecification().getTaskSets().get(4);
            Assert.assertEquals("coordinator", coordinatorTaskSet.getName());
            List<TaskSpecification> coordinatorTaskSpecifications = coordinatorTaskSet.getTaskSpecifications();
            Assert.assertEquals(1, coordinatorTaskSpecifications.size());
            TaskSpecification coordinatorTaskSpecification = coordinatorTaskSpecifications.get(0);
            Assert.assertEquals("coordinator-0", coordinatorTaskSpecification.getName());

        }

    }


}