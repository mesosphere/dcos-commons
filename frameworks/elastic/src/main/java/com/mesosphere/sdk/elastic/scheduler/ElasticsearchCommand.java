package com.mesosphere.sdk.elastic.scheduler;

import com.google.common.base.Joiner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ElasticsearchCommand {
    private static final int MINIMUM_MASTER_NODES = 2;
    private final String elasticsearchVerName;
    private final String xpackUri;
    private final String statsdUri;
    private final String elasticsearchPlugins;
    private final String serviceName;
    private final int masterTransportPort;
    private final int statsdUdpPort;
    private final String statsdUdpHost;

    ElasticsearchCommand(String elasticsearchVerName,
                         String xpackUri,
                         String elasticsearchPlugins,
                         String serviceName,
                         int masterTransportPort,
                         String statsdUri,
                         int statsdUdpPort,
                         String statsdUdpHost) {
        this.elasticsearchVerName = elasticsearchVerName;
        this.xpackUri = xpackUri;
        this.statsdUri = statsdUri;
        this.elasticsearchPlugins = elasticsearchPlugins;
        this.serviceName = serviceName;
        this.masterTransportPort = masterTransportPort;
        this.statsdUdpHost = statsdUdpHost;
        this.statsdUdpPort = statsdUdpPort;
    }

    String getCommandLineInvocation(String nodeName,
                                    int httpPort,
                                    int transportPort,
                                    String containerPath) {
        Properties properties = new Properties();
        properties.setProperty("discovery.zen.ping.unicast.hosts", eligibleMasterUnicastHosts(serviceName));
        properties.setProperty("discovery.zen.minimum_master_nodes", Integer.toString(MINIMUM_MASTER_NODES));
        properties.setProperty("network.host", "_site_,_local_");
        properties.setProperty("cluster.name", serviceName);
        properties.setProperty("node.name", nodeName);
        properties.setProperty("path.data", containerPath + "/data");
        properties.setProperty("path.logs", containerPath + "/logs");

        properties.setProperty("http.port", Integer.toString(httpPort));
        properties.setProperty("transport.tcp.port", Integer.toString(transportPort));

        properties.setProperty("bootstrap.ignore_system_bootstrap_checks", Boolean.toString(true));
        properties.setProperty("bootstrap.memory_lock", Boolean.toString(true));

        properties.setProperty("metrics.statsd.host", statsdUdpHost);
        properties.setProperty("metrics.statsd.port", Integer.toString(statsdUdpPort));

        properties.putAll(nodeTypePropertyOverrides(nodeName));

        List<String> commands = new ArrayList<>();
        commands.add("export PATH=$(ls -d $MESOS_SANDBOX/jre*/bin):$PATH");

        String binDir = String.format("$MESOS_SANDBOX/%1$s/bin", elasticsearchVerName);
        List<String> pluginsList = new ArrayList<>();
        Path xPackPath = Paths.get(xpackUri).getFileName();
        if (xPackPath != null) {
            String xpackFilename = xPackPath.toString();
            String xpackFilePath = String.format("file://$MESOS_SANDBOX/%1$s", xpackFilename);
            pluginsList.add(xpackFilePath);
        }
        Path statsdPath = Paths.get(statsdUri).getFileName();
        if (statsdPath != null) {
            String statsdFilename = statsdPath.toString();
            String statsdFilePath = String.format("file://$MESOS_SANDBOX/%1$s", statsdFilename);
            pluginsList.add(statsdFilePath);
        }
        String userSpecifiedPlugins = elasticsearchPlugins;
        if (userSpecifiedPlugins.length() > 0) {
            pluginsList.addAll(Arrays.asList(userSpecifiedPlugins.split(",")));
        }
        commands.addAll(pluginsList.stream()
            .map(plugin -> pluginInstallCommand(binDir, plugin))
            .collect(Collectors.toList()));

        commands.add("$MESOS_SANDBOX/dns_utils/wait_dns.sh");

        String elasticsearchProperties = properties.entrySet().stream()
            .map(this::entryAsParam)
            .collect(Collectors.joining(" "));
        commands.add(String.format("exec %1$s/elasticsearch " + elasticsearchProperties, binDir));
        return Joiner.on(" && ").join(commands);
    }

    private String pluginInstallCommand(String binDir, String plugin) {
        return String.format("%s/elasticsearch-plugin install --batch %s", binDir, plugin.trim());
    }

    private String entryAsParam(Map.Entry<Object, Object> e) {
        return "-E" + e.getKey() + "=" + e.getValue();
    }

    private String eligibleMasterUnicastHosts(String frameworkName) {
        List<String> masterList = Stream.of(0, 1, 2).
            map(nodeIndex -> masterNodeName(nodeIndex, frameworkName)).
            collect(Collectors.toList());
        return Joiner.on(",").join(masterList);
    }

    private String masterNodeName(Integer nodeIndex, String frameworkName) {
        return OfferUtils.idToName("master", nodeIndex) + "." + frameworkName + ".mesos:" + masterTransportPort;
    }

    private Properties nodeTypePropertyOverrides(String nodeName) {
        String nodeType = OfferUtils.nameToTaskType(nodeName);
        Properties properties = new Properties();
        properties.setProperty("node.master", Boolean.toString(false));
        properties.setProperty("node.data", Boolean.toString(false));
        properties.setProperty("node.ingest", Boolean.toString(false));
        switch (nodeType) {
            case "master":
                properties.setProperty("node.master", Boolean.toString(true));
                break;
            case "data":
                properties.setProperty("node.data", Boolean.toString(true));
                break;
            case "ingest":
                properties.setProperty("node.ingest", Boolean.toString(true));
                break;
            default:
        }
        return properties;
    }

}
