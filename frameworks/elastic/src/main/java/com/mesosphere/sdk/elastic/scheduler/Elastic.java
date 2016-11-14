package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.offer.ValueUtils;
import org.apache.mesos.offer.constrain.TaskTypeRule;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.specification.*;

import java.util.*;
import java.util.stream.Collectors;

class Elastic {
    static final String MASTER_NODE_TYPE_NAME = "master";
    static final String DATA_NODE_TYPE_NAME = "data";
    static final String INGEST_NODE_TYPE_NAME = "ingest";
    static final String COORDINATOR_NODE_TYPE_NAME = "coordinator";
    static final String KIBANA_TYPE_NAME = "kibana";
    private static final int MASTER_NODE_COUNT = 3;
    private static final String CONTAINER_PATH_SUFFIX = "-container-path";
    private static final String FRAMEWORK_USER = System.getenv("FRAMEWORK_USER");
    private static final String FRAMEWORK_ID = System.getenv("MESOS_FRAMEWORK_ID");
    private static final int KIBANA_GRACE_PERIOD_SECONDS = 900;
    private static final int ELASTICSEARCH_GRACE_PERIOD_SECONDS = 600;
    private static final String DNS_UTILS_URI = System.getenv("DNS_UTILS_URI");
    private static final String DIAGNOSTICS_URI = System.getenv("DIAGNOSTICS_URI");
    private static final String STATSD_PLUGIN_URI = System.getenv("STATSD_PLUGIN_URI");
    private static final String JAVA_URI = System.getenv("JAVA_URI");
    private static final String ELASTICSEARCH_URI = System.getenv("ELASTICSEARCH_URI");
    private static final String KIBANA_URI = System.getenv("KIBANA_URI");
    private static final String XPACK_URI = System.getenv("XPACK_URI");
    private static final String SERVICE_NAME = System.getenv("SERVICE_NAME");
    private static final String ROLE = SchedulerUtils.nameToRole(SERVICE_NAME);
    private static final String PRINCIPAL = SchedulerUtils.nameToPrincipal(SERVICE_NAME);
    private final double masterNodeCpus = Double.valueOf(System.getenv("MASTER_NODE_CPUS"));
    private final double masterNodeMem = Double.valueOf(System.getenv("MASTER_NODE_MEM"));
    private final double masterNodeDisk = Double.valueOf(System.getenv("MASTER_NODE_DISK"));
    private final boolean masterNodeMountedDisk = Boolean.valueOf(System.getenv("MASTER_NODE_MOUNTED_DISK"));
    private final int masterNodeHeapMb = Integer.parseInt(System.getenv("MASTER_NODE_HEAP_MB"));
    private final int masterNodeHttpPort = Integer.parseInt(System.getenv("MASTER_NODE_HTTP_PORT"));
    private final int masterNodeTransportPort = Integer.parseInt(System.getenv("MASTER_NODE_TRANSPORT_PORT"));
    private final int dataNodeCount = Integer.parseInt(System.getenv("DATA_NODE_COUNT"));
    private final double dataNodeCpus = Double.valueOf(System.getenv("DATA_NODE_CPUS"));
    private final double dataNodeMem = Double.valueOf(System.getenv("DATA_NODE_MEM"));
    private final double dataNodeDisk = Double.valueOf(System.getenv("DATA_NODE_DISK"));
    private final boolean dataNodeMountedDisk = Boolean.valueOf(System.getenv("DATA_NODE_MOUNTED_DISK"));
    private final int dataNodeHeapMb = Integer.parseInt(System.getenv("DATA_NODE_HEAP_MB"));
    private final int dataNodeHttpPort = Integer.parseInt(System.getenv("DATA_NODE_HTTP_PORT"));
    private final int dataNodeTransportPort = Integer.parseInt(System.getenv("DATA_NODE_TRANSPORT_PORT"));
    private final int ingestNodeCount = Integer.parseInt(System.getenv("INGEST_NODE_COUNT"));
    private final double ingestNodeCpus = Double.valueOf(System.getenv("INGEST_NODE_CPUS"));
    private final double ingestNodeMem = Double.valueOf(System.getenv("INGEST_NODE_MEM"));
    private final double ingestNodeDisk = Double.valueOf(System.getenv("INGEST_NODE_DISK"));
    private final boolean ingestNodeMountedDisk = Boolean.valueOf(System.getenv("INGEST_NODE_MOUNTED_DISK"));
    private final int ingestNodeHeapMb = Integer.parseInt(System.getenv("INGEST_NODE_HEAP_MB"));
    private final int ingestNodeHttpPort = Integer.parseInt(System.getenv("INGEST_NODE_HTTP_PORT"));
    private final int ingestNodeTransportPort = Integer.parseInt(System.getenv("INGEST_NODE_TRANSPORT_PORT"));
    private final int coordinatorNodeCount = Integer.parseInt(System.getenv("COORDINATOR_NODE_COUNT"));
    private final double coordinatorNodeCpus = Double.valueOf(System.getenv("COORDINATOR_NODE_CPUS"));
    private final double coordinatorNodeMem = Double.valueOf(System.getenv("COORDINATOR_NODE_MEM"));
    private final double coordinatorNodeDisk = Double.valueOf(System.getenv("COORDINATOR_NODE_DISK"));
    private final boolean coordinatorNodeMountedDisk = Boolean.valueOf(System.getenv("COORDINATOR_NODE_MOUNTED_DISK"));
    private final int coordinatorNodeHeapMb = Integer.parseInt(
        System.getenv("COORDINATOR_NODE_HEAP_MB"));
    private final int coordinatorNodeHttpPort = Integer.parseInt(System.getenv("COORDINATOR_NODE_HTTP_PORT"));
    private final int coordinatorNodeTransportPort = Integer.parseInt(
        System.getenv("COORDINATOR_NODE_TRANSPORT_PORT"));
    private final int kibanaCount = Integer.parseInt(System.getenv("KIBANA_COUNT"));
    private final double kibanaCpus = Double.valueOf(System.getenv("KIBANA_CPUS"));
    private final double kibanaMem = Double.valueOf(System.getenv("KIBANA_MEM"));
    private final double kibanaDisk = Double.valueOf(System.getenv("KIBANA_DISK"));
    private final boolean kibanaMountedDisk = Boolean.valueOf(System.getenv("KIBANA_MOUNTED_DISK"));
    private final int kibanaPort = Integer.parseInt(System.getenv("KIBANA_PORT"));
    private final String kibanaPassword = System.getenv("KIBANA_PASSWORD");
    private final ElasticsearchCommand elasticsearchCommand;
    private final KibanaCommand kibanaCommand;
    private final ServiceSpecification serviceSpecification;

    Elastic() {
        String elasticsearchVerName = System.getenv("ELASTICSEARCH_VER_NAME");
        String elasticsearchPlugins = System.getenv("ELASTICSEARCH_PLUGINS");
        elasticsearchCommand = new ElasticsearchCommand(elasticsearchVerName, XPACK_URI,
            elasticsearchPlugins, SERVICE_NAME, masterNodeTransportPort, STATSD_PLUGIN_URI);
        String kibanaVerName = System.getenv("KIBANA_VER_NAME");
        kibanaCommand = new KibanaCommand(kibanaVerName, XPACK_URI);
        serviceSpecification = new DefaultServiceSpecification(SERVICE_NAME, createTaskSets());
    }

    List<ConfigurationValidator<ServiceSpecification>> configValidators() {
        return Collections.singletonList(new HeapCannotExceedHalfMem());
    }

    ServiceSpecification getServiceSpecification() {
        return serviceSpecification;
    }

    private List<TaskSet> createTaskSets() {
        List<TaskSet> taskSets = new ArrayList<>();
        if (kibanaCount > 0) {
            taskSets.add(createKibanaTaskSet());
        }
        List<DefaultTaskSet> requiredTaskSets = Arrays.asList(
            createElasticsearchTaskSet(MASTER_NODE_TYPE_NAME, MASTER_NODE_COUNT, masterNodeCpus, masterNodeDisk,
                masterNodeMountedDisk, masterNodeMem, masterNodeHeapMb, masterNodeHttpPort, masterNodeTransportPort),
            createElasticsearchTaskSet(DATA_NODE_TYPE_NAME, dataNodeCount, dataNodeCpus, dataNodeDisk,
                dataNodeMountedDisk, dataNodeMem, dataNodeHeapMb, dataNodeHttpPort, dataNodeTransportPort),
            createElasticsearchTaskSet(INGEST_NODE_TYPE_NAME, ingestNodeCount, ingestNodeCpus, ingestNodeDisk,
                ingestNodeMountedDisk, ingestNodeMem, ingestNodeHeapMb, ingestNodeHttpPort, ingestNodeTransportPort));
        taskSets.addAll(requiredTaskSets);
        if (coordinatorNodeCount > 0) {
            taskSets.add(createElasticsearchTaskSet(COORDINATOR_NODE_TYPE_NAME, coordinatorNodeCount,
                coordinatorNodeCpus, coordinatorNodeDisk, coordinatorNodeMountedDisk, coordinatorNodeMem,
                coordinatorNodeHeapMb, coordinatorNodeHttpPort, coordinatorNodeTransportPort));
        }
        return taskSets;
    }

    private DefaultTaskSet createKibanaTaskSet() {
        String nodeTypeName = KIBANA_TYPE_NAME;
        String containerPath = createContainerPath(nodeTypeName);
        List<TaskSpecification> taskSpecifications = new ArrayList<>();
        Collection<ConfigFileSpecification> configFileSpecs = new ArrayList<>();
        KibanaConfigurator kibanaConfigurator = new KibanaConfigurator("kibana.yml");
        configFileSpecs.add(kibanaConfigurator.getKibanaConfigFileSpecification());
        for (int i = 0; i < kibanaCount; i++) {
            ElasticsearchTaskSpecification defaultTaskSpecification = new ElasticsearchTaskSpecification(
                OfferUtils.idToName(nodeTypeName, i),
                nodeTypeName,
                createKibanaCommandInfo(i),
                createResources(kibanaCpus, kibanaMem, Collections.singletonList(kibanaPort)),
                createVolumeSpecifications(kibanaDisk, containerPath, kibanaMountedDisk),
                configFileSpecs,
                Optional.of(TaskTypeRule.colocateWith(COORDINATOR_NODE_TYPE_NAME)),
                Optional.of(createKibanaHealthCheck(kibanaPort)));
            taskSpecifications.add(defaultTaskSpecification);
        }

        return DefaultTaskSet.create(nodeTypeName, taskSpecifications);
    }

    private DefaultTaskSet createElasticsearchTaskSet(String nodeTypeName, int nodeCount, double cpu, double diskMb,
                                                      boolean mountedDisk, double memMb, int heapSize, int httpPort,
                                                      int transportPort) {
        String containerPath = createContainerPath(nodeTypeName);
        Collection<ConfigFileSpecification> configFileSpecs = new ArrayList<>();
        List<TaskSpecification> taskSpecifications = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            ElasticsearchTaskSpecification defaultTaskSpecification = new ElasticsearchTaskSpecification(
                OfferUtils.idToName(nodeTypeName, i),
                nodeTypeName,
                createElasticsearchCommandInfo(nodeTypeName, i, heapSize, httpPort, transportPort, containerPath),
                createResources(cpu, memMb, Arrays.asList(httpPort, transportPort)),
                createVolumeSpecifications(diskMb, containerPath, mountedDisk),
                configFileSpecs,
                Optional.of(TaskTypeRule.avoid(nodeTypeName)),
                Optional.of(createElasticsearchHealthCheck(httpPort)));
            taskSpecifications.add(defaultTaskSpecification);
        }

        return DefaultTaskSet.create(nodeTypeName, taskSpecifications);
    }

    private Protos.HealthCheck createElasticsearchHealthCheck(int port) {
        String command = String.format("curl -I -s -f -u kibana:%s localhost:%d", kibanaPassword, port);
        return OfferUtils.createCommandHealthCheck(command, ELASTICSEARCH_GRACE_PERIOD_SECONDS);
    }

    private Protos.HealthCheck createKibanaHealthCheck(int port) {
        String command = String.format("curl -I -s -f localhost:%d/login", port);
        return OfferUtils.createCommandHealthCheck(command, KIBANA_GRACE_PERIOD_SECONDS);
    }

    private Collection<ResourceSpecification> createResources(double cpu, double memMb, List<Integer> individualPorts) {
        Protos.Resource ports = ResourceUtils.getUnreservedRanges("ports", createIndividualPortRanges(individualPorts));
        return Arrays.asList(
            new DefaultResourceSpecification(
                "cpus",
                ValueUtils.getValue(ResourceUtils.getUnreservedScalar("cpus", cpu)),
                ROLE,
                PRINCIPAL),
            new DefaultResourceSpecification(
                "mem",
                ValueUtils.getValue(ResourceUtils.getUnreservedScalar("mem", memMb)),
                ROLE,
                PRINCIPAL),
            new DefaultResourceSpecification(
                "ports",
                ValueUtils.getValue(ports),
                ROLE,
                PRINCIPAL));
    }

    private Collection<VolumeSpecification> createVolumeSpecifications(double diskMb, String containerPath,
                                                                       boolean mountedDisk) {
        VolumeSpecification volumeSpecification = new DefaultVolumeSpecification(
            diskMb,
            mountedDisk ? VolumeSpecification.Type.MOUNT : VolumeSpecification.Type.ROOT,
            containerPath,
            ROLE,
            PRINCIPAL);

        return Collections.singletonList(volumeSpecification);
    }

    private Protos.CommandInfo createElasticsearchCommandInfo(String nodeTypeName, int nodeId, int heapSize,
                                                              int httpPort, int transportPort, String containerPath) {
        final String cmd = elasticsearchCommand.getCommandLineInvocation(OfferUtils.idToName(nodeTypeName, nodeId),
            httpPort, transportPort, containerPath);

        Map<String, String> environmentMap = new HashMap<>();
        // ES_JAVA_OPTS used by Elasticsearch to manage heap
        environmentMap.put("ES_JAVA_OPTS", OfferUtils.elasticsearchHeapOpts(heapSize));
        // FRAMEWORK_NAME env var needed by dns_utils/wait_dns.sh
        environmentMap.put("FRAMEWORK_NAME", SERVICE_NAME);
        return Protos.CommandInfo.newBuilder()
            .setValue(cmd)
            .setUser(FRAMEWORK_USER)
            .addUris(TaskUtils.uri(DNS_UTILS_URI))
            .addUris(TaskUtils.uri(JAVA_URI))
            .addUris(TaskUtils.uri(ELASTICSEARCH_URI))
            .addUris(TaskUtils.uri(XPACK_URI))
            .addUris(TaskUtils.uri(DIAGNOSTICS_URI))
            .addUris(TaskUtils.uri(STATSD_PLUGIN_URI))
            .setEnvironment(TaskUtils.fromMapToEnvironment(environmentMap))
            .build();
    }

    private Protos.CommandInfo createKibanaCommandInfo(int nodeId) {
        final String cmd = kibanaCommand.getCommandLineInvocation();
        Map<String, String> environmentMap = new HashMap<>();
        String master = String.format("http://master-0.%s.mesos:%d", SERVICE_NAME, masterNodeHttpPort);
        environmentMap.put("KIBANA_ELASTICSEARCH_URL", master);
        environmentMap.put("KIBANA_SERVER_NAME", OfferUtils.idToName("kibana", nodeId));
        environmentMap.put("KIBANA_PASSWORD", kibanaPassword);
        environmentMap.put("KIBANA_PORT", Integer.toString(kibanaPort));
        environmentMap.put("KIBANA_ENCRYPTION_KEY", FRAMEWORK_ID);
        return Protos.CommandInfo.newBuilder()
            .setValue(cmd)
            .setUser(FRAMEWORK_USER)
            .addUris(TaskUtils.uri(KIBANA_URI))
            .addUris(TaskUtils.uri(XPACK_URI))
            .setEnvironment(TaskUtils.fromMapToEnvironment(environmentMap))
            .build();
    }

    private List<Protos.Value.Range> createIndividualPortRanges(List<Integer> individualPorts) {
        return individualPorts.stream().map(OfferUtils::buildSinglePortRange).collect(Collectors.toList());
    }

    private String createContainerPath(String nodeTypeName) {
        return nodeTypeName + CONTAINER_PATH_SUFFIX;
    }

}
