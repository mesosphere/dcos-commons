package com.mesosphere.sdk.hdfs.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.ResourceUtils;
import org.apache.mesos.offer.ValueUtils;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;
import org.apache.mesos.offer.constrain.TaskTypeGenerator;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.specification.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * Main entry point for the HDFS Scheduler.
 */
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final String SERVICE_NAME = "hdfs";
    private static final String HDFS_VERSION = "hadoop-2.6.0-cdh5.7.1";
    private static final String HDFS_SITE_CONFIG_PATH = String.format("%s/etc/hadoop/hdfs-site.xml", HDFS_VERSION);
    private static final String CORE_SITE_CONFIG_PATH = String.format("%s/etc/hadoop/core-site.xml", HDFS_VERSION);

    private static final String JOURNAL_NODE_NAME = "journalnode";
    private static final int JOURNAL_NODE_COUNT = Integer.parseInt(System.getenv("JOURNAL_NODE_COUNT"));
    private static final double JOURNAL_NODE_CPU = Double.valueOf(System.getenv("JOURNAL_NODE_CPUS"));
    private static final double JOURNAL_NODE_MEM_MB = Double.valueOf(System.getenv("JOURNAL_NODE_MEMORY_MB"));
    private static final double JOURNAL_NODE_DISK_MB = Double.valueOf(System.getenv("JOURNAL_NODE_DISK_MB"));

    private static final String NAME_NODE_NAME = "namenode";
    private static final int NAME_NODE_COUNT = Integer.parseInt(System.getenv("NAME_NODE_COUNT"));
    private static final double NAME_NODE_CPU = Double.valueOf(System.getenv("NAME_NODE_CPUS"));
    private static final double NAME_NODE_MEM_MB = Double.valueOf(System.getenv("NAME_NODE_MEMORY_MB"));
    private static final double NAME_NODE_DISK_MB = Double.valueOf(System.getenv("NAME_NODE_DISK_MB"));

    private static final String DATA_NODE_NAME = "datanode";
    private static final int DATA_NODE_COUNT = Integer.parseInt(System.getenv("DATA_NODE_COUNT"));
    private static final double DATA_NODE_CPU = Double.valueOf(System.getenv("DATA_NODE_CPUS"));
    private static final double DATA_NODE_MEM_MB = Double.valueOf(System.getenv("DATA_NODE_MEMORY_MB"));
    private static final double DATA_NODE_DISK_MB = Double.valueOf(System.getenv("DATA_NODE_DISK_MB"));

    private static final int API_PORT = Integer.parseInt(System.getenv("PORT0"));
    private static final String CONTAINER_PATH_SUFFIX = "volume";
    private static final String ROLE = SchedulerUtils.nameToRole(SERVICE_NAME);
    private static final String PRINCIPAL = SchedulerUtils.nameToPrincipal(SERVICE_NAME);
    private static final String HDFS_URI =
            "https://downloads.mesosphere.com/hdfs/assets/hadoop-2.6.0-cdh5.7.1-dcos.tar.gz";

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting reference scheduler with args: " + Arrays.asList(args));
        new DefaultService(API_PORT).register(getServiceSpecification());
    }

    private static ServiceSpecification getServiceSpecification() {
        return new DefaultServiceSpecification(
                SERVICE_NAME,
                Arrays.asList(
                        HDFSTaskSet.create(JOURNAL_NODE_COUNT,
                                JOURNAL_NODE_NAME,
                                // command is defined in constructor
                                getResources(JOURNAL_NODE_CPU, JOURNAL_NODE_MEM_MB),
                                getVolumes(JOURNAL_NODE_DISK_MB),
                                getHDFSConfigFiles(),
                                Optional.of(TaskTypeGenerator.createAvoid(JOURNAL_NODE_NAME)),
                                Optional.empty()),
                        HDFSTaskSet.create(NAME_NODE_COUNT,
                                NAME_NODE_NAME,
                                // command is defined in constructor
                                getResources(NAME_NODE_CPU, NAME_NODE_MEM_MB),
                                getVolumes(NAME_NODE_DISK_MB),
                                getHDFSConfigFiles(),
                                Optional.of(TaskTypeGenerator.createAvoid(NAME_NODE_NAME)),
                                Optional.empty()),
                        HDFSTaskSet.create(DATA_NODE_COUNT,
                                DATA_NODE_NAME,
                                // command is defined in constructor
                                getResources(DATA_NODE_CPU, DATA_NODE_MEM_MB),
                                getVolumes(DATA_NODE_DISK_MB),
                                getHDFSConfigFiles(),
                                Optional.of(TaskTypeGenerator.createAvoid(DATA_NODE_NAME)),
                                Optional.empty())
                )
        );
    }

    private static class HDFSTaskSet extends DefaultTaskSet {
        public static HDFSTaskSet create(
                int count,
                String name,
                Collection<ResourceSpecification> resources,
                Collection<VolumeSpecification> volumes,
                Collection<ConfigFileSpecification> configs,
                Optional<PlacementRuleGenerator> placementOptional,
                Optional<Protos.HealthCheck> healthCheck) {

            ArrayList taskSpecifications = new ArrayList();

            for (int i = 0; i < count; ++i) {
                taskSpecifications.add(new HDFSTaskSpecification(
                        name + "-" + i,
                        name, getCommand(name, i), resources, volumes, configs, placementOptional, healthCheck));
            }

            return new HDFSTaskSet(name, taskSpecifications);

        }

        protected HDFSTaskSet(String name, List<TaskSpecification> taskSpecifications) {
            super(name, taskSpecifications);
        }

        private static class HDFSTaskSpecification extends DefaultTaskSpecification {
            public HDFSTaskSpecification(
                    String name,
                    String type,
                    Protos.CommandInfo commandInfo,
                    Collection<ResourceSpecification> resourceSpecifications,
                    Collection<VolumeSpecification> volumeSpecifications,
                    Collection<ConfigFileSpecification> configFileSpecifications,
                    Optional<PlacementRuleGenerator> placementOptional,
                    Optional<Protos.HealthCheck> healthCheck) {
                super(name, type, commandInfo, resourceSpecifications, volumeSpecifications,
                        configFileSpecifications, placementOptional, healthCheck);
            }
        }
    }

    private static Collection<ResourceSpecification> getResources(double cpu, double memMb) {
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
                        PRINCIPAL));
    }

    private static Collection<VolumeSpecification> getVolumes(double diskMb) {
        VolumeSpecification volumeSpecification = new DefaultVolumeSpecification(
                diskMb,
                VolumeSpecification.Type.ROOT,
                CONTAINER_PATH_SUFFIX,
                ROLE,
                PRINCIPAL);

        return Arrays.asList(volumeSpecification);
    }

    private static Protos.CommandInfo getCommand(String nodeType, int instanceIndex) {
        String cmd = "env && " +
                resolveDNSName(nodeType, instanceIndex) +
                createVolumeDirectory(nodeType);

        if (nodeType.equals(NAME_NODE_NAME)) {
            cmd += addNameNodeBootstrapCommands(instanceIndex);
        } else {
            cmd += "./%s/bin/hdfs %s";
            cmd = String.format(cmd, HDFS_VERSION, nodeType);
        }

        return Protos.CommandInfo.newBuilder()
                .addUris(Protos.CommandInfo.URI.newBuilder().setValue(HDFS_URI))
                .setValue(cmd)
                .build();
    }

    private static String resolveDNSName(String nodeType, int instanceIndex) {
        String name = String.format("%s-%d.%s.mesos", nodeType, instanceIndex, SERVICE_NAME);
        String script = "echo 'Resolving " + name + "';" +
                "while [ -z `dig +short " + name + "` ]; do " +
                "echo 'Cannot resolve DNS name: " + name + "'; " +
                "dig +short " + name + "; " +
                "sleep 1; done; echo 'Resolved name: " + name + "';" +
                "dig +short " + name + ";";
        return script;
    }

    private static String addNameNodeBootstrapCommands(int instanceIndex) {
        if (instanceIndex == 0) {
            return String.format("echo 'Formating filesystem';" +
                    "./%s/bin/hdfs namenode -format -force && " +
                    "echo 'Initialize Shared Edits' && " +
                    "./%s/bin/hdfs namenode -initializeSharedEdits -force && " +
                    "echo 'Starting Name Node' && " +
                    "./%s/bin/hdfs namenode", HDFS_VERSION, HDFS_VERSION, HDFS_VERSION
            );
        } else {
            return String.format("echo 'sleeping for 30 seconds to let namenode-0 bootstrap and come up';" +
                    "sleep 30;" +
                    "echo 'NameNode bootstrap';" +
                    "./%s/bin/hdfs namenode -bootstrapStandBy -force && " +
                    "echo 'Starting Name Node' && " +
                    "./%s/bin/hdfs namenode", HDFS_VERSION, HDFS_VERSION
            );
        }
    }

    private static String createVolumeDirectory(String nodeType) {
        List<String> dirNames = new ArrayList<>();
        StringBuilder command = new StringBuilder();
        if (nodeType.equals(JOURNAL_NODE_NAME)) {
            dirNames.add("volume/hdfs/data/jn");
            dirNames.add("/tmp/hadoop/dfs/journalnode/hdfs");
            command.append("echo 'Creating Journal Node edits directory';");
        } else if (nodeType.equals(DATA_NODE_NAME)) {
            dirNames.add("volume/hdfs/data/jn");
            command.append("echo 'Creating Data Node data directory';");
        } else {
            dirNames.add("volume/hdfs/data/name");
            command.append("echo 'Creating Name Node name directory';");
        }

        for (String dir : dirNames) {
            command.append(String.format("mkdir -p %s;", dir));
        }

        return command.toString();
    }

    private static List<ConfigFileSpecification> getHDFSConfigFiles() {
        ConfigFileSpecification HDFS_SITE_CONFIG =
                new DefaultConfigFileSpecification(HDFS_SITE_CONFIG_PATH, convertFileToString("hdfs-site.xml"));
        ConfigFileSpecification CORE_SITE_CONFIG =
                new DefaultConfigFileSpecification(CORE_SITE_CONFIG_PATH, convertFileToString("core-site.xml"));
        return Arrays.asList(HDFS_SITE_CONFIG, CORE_SITE_CONFIG);
    }

    private static String convertFileToString(String localTemplatePath) {
        StringBuilder stringBuilder = null;
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(new FileInputStream(localTemplatePath), "utf-8"))) {
            stringBuilder = new StringBuilder();
            String line = bufferedReader.readLine();

            while (line != null) {
                stringBuilder.append(line);
                stringBuilder.append(System.lineSeparator());
                line = bufferedReader.readLine();
            }

        } catch (FileNotFoundException e) {
            LOGGER.info("The local template file wasn't found: {}", e);
            System.exit(1);
        } catch (IOException e) {
            LOGGER.info("Couldn't read the file: {}", e);
            System.exit(1);
        }

        return stringBuilder.toString();
    }
}
