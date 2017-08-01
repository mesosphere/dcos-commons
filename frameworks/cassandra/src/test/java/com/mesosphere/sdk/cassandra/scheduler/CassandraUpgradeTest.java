package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

/**
 * This class tests the {@link CassandraUpgrade} class.
 */
public class CassandraUpgradeTest extends BaseServiceSpecTest {
    public CassandraUpgradeTest() {
        super(
                "EXECUTOR_URI", "http://executor.uri",
                "BOOTSTRAP_URI", "http://bootstrap.uri",
                "SCHEDULER_URI", "http://scheduler.uri",
                "CASSANDRA_URI", "http://cassandra.uri",
                "LIBMESOS_URI", "http://libmesos.uri",
                "CASSANDRA_DOCKER_IMAGE", "docker/cassandra",
                "PORT_API", "8080",

                "SERVICE_NAME", "cassandra",
                "TASKCFG_ALL_CASSANDRA_CLUSTER_NAME", "cassandra",
                "NODES", "3",
                "SERVICE_USER", "core",
                "SERVICE_ROLE", "role",
                "SERVICE_PRINCIPAL", "principal",
                "CASSANDRA_CPUS", "0.1",
                "CASSANDRA_VERSION", "3.0.13",
                "CASSANDRA_MEMORY_MB", "512",
                "TASKCFG_ALL_JMX_PORT", "9000",
                "TASKCFG_ALL_CASSANDRA_STORAGE_PORT", "9001",
                "TASKCFG_ALL_CASSANDRA_SSL_STORAGE_PORT", "9002",
                "TASKCFG_ALL_CASSANDRA_NATIVE_TRANSPORT_PORT", "9003",
                "TASKCFG_ALL_CASSANDRA_RPC_PORT", "9004",
                "TASKCFG_ALL_CASSANDRA_HEAP_SIZE_MB", "4000",
                "TASKCFG_ALL_CASSANDRA_HEAP_NEW_MB", "400",
                "CASSANDRA_HEAP_GC", "CMS",
                "CASSANDRA_DISK_MB", "5000",
                "CASSANDRA_DISK_TYPE", "ROOT");
    }

    @Test
    public void testNeedsUpgradePositive() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("old_svc.yml");
        Assert.assertTrue(CassandraUpgrade.needsUpgrade(serviceSpec));
    }

    @Test
    public void testNeedsUpgradeNegative() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("svc.yml");
        Assert.assertFalse(CassandraUpgrade.needsUpgrade(serviceSpec));
    }

    @Test
    public void testUpgradeServiceSpecPositive() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("old_svc.yml");
        Assert.assertTrue(CassandraUpgrade.needsUpgrade(serviceSpec));
        ServiceSpec upgradedSpec = CassandraUpgrade.upgradeServiceSpec(serviceSpec);
        Assert.assertFalse(CassandraUpgrade.needsUpgrade(upgradedSpec));
        Assert.assertTrue(
                upgradedSpec.getPods().stream()
                        .flatMap(podSpec -> podSpec.getTasks().stream())
                        .map(taskSpec -> taskSpec.getResourceSet())
                        .allMatch(resourceSet -> resourceSet.getVolumes().isEmpty()));
    }

    @Test
    public void testUpgradePodSpec() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("old_svc.yml");
        PodSpec podSpec = serviceSpec.getPods().get(0);
        PodSpec upgradedSpec = CassandraUpgrade.upgradePodSpec(podSpec);
        Assert.assertEquals(1, upgradedSpec.getVolumes().size());
    }

    @Test
    public void testUpgradeResourceSet() throws Exception {
        ServiceSpec serviceSpec = getServiceSpec("old_svc.yml");
        PodSpec podSpec = serviceSpec.getPods().get(0);
        TaskSpec taskSpec = podSpec.getTasks().get(0);
        ResourceSet resourceSet = taskSpec.getResourceSet();
        Assert.assertEquals(1, resourceSet.getVolumes().size());
        ResourceSet upgradedResourceSet = CassandraUpgrade.upgradeResourceSet(resourceSet);
        Assert.assertEquals(0, upgradedResourceSet.getVolumes().size());
    }

    /*
    Example disk resource which should be moved from the TaskInfo to the ExecutorInfo
    {
        "providerId": null,
            "name": "disk",
            "type": "SCALAR",
            "scalar": {
        "value": 10240.0
    },
        "ranges": null,
            "set": null,
            "role": null,
            "allocationInfo": null,
            "reservation": null,
            "reservations": [
        {
            "type": "DYNAMIC",
                "role": "cassandra-role",
                "principal": "cassandra-principal",
                "labels": {
            "labels": [
            {
                "key": "resource_id",
                    "value": "104c10ca-c4e9-40af-a955-2b4a28826260"
            }
                  ]
        }
        }
            ],
        "disk": {
        "persistence": {
            "id": "ea19189c-5e3e-4424-834c-4cfb4bcc971e",
                    "principal": "cassandra-principal"
        },
        "volume": {
            "mode": "RW",
                    "containerPath": "container-path",
                    "hostPath": null,
                    "image": null,
                    "source": null
        },
        "source": null
    },
        "revocable": null,
            "shared": null
    }
    */
    @Test
    public void testUpgradeTaskInfo() {
        Protos.Resource diskResource = Protos.Resource.newBuilder()
                .setName("disk")
                .setType(Protos.Value.Type.SCALAR)
                .setScalar(Protos.Value.Scalar.newBuilder().setValue(10240))
                .addReservations(Protos.Resource.ReservationInfo.newBuilder()
                        .setType(Protos.Resource.ReservationInfo.Type.DYNAMIC)
                        .setRole("cassandra-role")
                        .setPrincipal("cassandra-principal")
                        .setLabels(
                                Protos.Labels.newBuilder()
                                .addLabels(Protos.Label.newBuilder()
                                        .setKey("resource_id")
                                        .setValue(UUID.randomUUID().toString()))))
                .setDisk(Protos.Resource.DiskInfo.newBuilder()
                        .setPersistence(Protos.Resource.DiskInfo.Persistence.newBuilder()
                                .setId(UUID.randomUUID().toString())
                                .setPrincipal("cassandra-principal"))
                        .setVolume(Protos.Volume.newBuilder()
                                .setMode(Protos.Volume.Mode.RW)
                                .setContainerPath("container-path")))
                .build();

        Protos.ExecutorInfo executorInfo = Protos.ExecutorInfo.newBuilder()
                .setExecutorId(Protos.ExecutorID.newBuilder().setValue(UUID.randomUUID().toString()))
                .build();

        Protos.TaskInfo taskInfo = TestConstants.TASK_INFO.toBuilder()
                .addResources(diskResource)
                .setExecutor(executorInfo)
                .build();

        Assert.assertEquals(1, taskInfo.getResourcesCount());
        Assert.assertEquals(0, taskInfo.getExecutor().getResourcesCount());

        taskInfo = CassandraUpgrade.moveDiskResourceToExecutor(taskInfo);

        Assert.assertEquals(0, taskInfo.getResourcesCount());
        Assert.assertEquals(1, taskInfo.getExecutor().getResourcesCount());
        Assert.assertEquals(diskResource, taskInfo.getExecutor().getResources(0));
    }

    @Test
    public void testSetTargetId() throws TaskException {
        UUID targetId = UUID.randomUUID();
        Collection<Protos.TaskInfo> labeledTaskInfos = CassandraUpgrade.setTargetId(
                targetId,
                Arrays.asList(TestConstants.TASK_INFO));

        Assert.assertEquals(1, labeledTaskInfos.size());
        Assert.assertEquals(
                targetId,
                new TaskLabelReader(labeledTaskInfos.stream().findFirst().get()).getTargetConfiguration());
    }
}
