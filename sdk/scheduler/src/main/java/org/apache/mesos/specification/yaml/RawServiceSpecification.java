package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections.CollectionUtils;

import java.time.Duration;
import java.util.*;

/**
 * Root of the parsed YAML object model.
 */
public class RawServiceSpecification {
    private String name;
    private String principal;
    private Integer apiPort;
    private String zookeeper;
    private LinkedHashMap<String, RawPod> pods;
    private Collection<RawPlan> plans;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getPrincipal() {
        return principal;
    }

    @JsonProperty("principal")
    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public Integer getApiPort() {
        return apiPort;
    }

    @JsonProperty("api-port")
    public void setApiPort(Integer apiPort) {
        this.apiPort = apiPort;
    }

    public String getZookeeper() {
        return zookeeper;
    }

    @JsonProperty("zookeeper")
    public void setZookeeper(String zookeeper) {
        this.zookeeper = zookeeper;
    }

    public LinkedHashMap<String, RawPod> getPods() {
        return pods;
    }

    @JsonProperty("pods")
    public void setPods(LinkedHashMap<String, RawPod> pods) {
        this.pods = pods;
    }

    public Collection<RawPlan> getPlans() {
        return plans;
    }

    @JsonProperty("plans")
    public void setPlans(Collection<RawPlan> plans) {
        this.plans = plans;
    }
}

class RawPod {
    private String name;
    private String placement;
    private Integer count;
    private String strategy;
    private String user;
    private LinkedHashMap<String, RawTask> tasks;
    private Collection<RawResourceSet> resourceSets;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Collection<RawResourceSet> getResourceSets() {
        return resourceSets;
    }

    @JsonProperty("resource-sets")
    public void setResourceSets(Collection<RawResourceSet> resourceSets) {
        this.resourceSets = resourceSets;
    }

    public String getPlacement() {
        return placement;
    }

    @JsonProperty("placement")
    public void setPlacement(String placement) {
        this.placement = placement;
    }

    public Integer getCount() {
        return count;
    }

    @JsonProperty("count")
    public void setCount(Integer count) {
        this.count = count;
    }

    public String getStrategy() {
        return strategy;
    }

    @JsonProperty("strategy")
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public LinkedHashMap<String, RawTask> getTasks() {
        return tasks;
    }

    @JsonProperty("tasks")
    public void setTasks(LinkedHashMap<String, RawTask> tasks) {
        this.tasks = tasks;
    }

    public String getUser() {
        return user;
    }

    @JsonProperty("user")
    public void setUser(String user) {
        this.user = user;
    }
}

class RawTask {
    private String name;
    private String goal;
    private String cmd;
    private String image;
    private Map<String, String> env;
    private Collection<RawConfiguration> configurations;
    private Collection<String> uris;
    private Double cpus;
    private Integer memory;
    private Collection<RawPort> ports;
    private LinkedHashMap<String, RawHealthCheck> healthChecks;
    private Collection<RawVolume> volumes;
    private String resourceSet;

    public Double getCpus() {
        return cpus;
    }

    @JsonProperty("cpus")
    public void setCpus(Double cpus) {
        this.cpus = cpus;
    }

    public Integer getMemory() {
        return memory;
    }

    @JsonProperty("memory")
    public void setMemory(Integer memory) {
        this.memory = memory;
    }

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getResourceSet() {
        return resourceSet;
    }

    @JsonProperty("resource-set")
    public void setResourceSet(String resourceSet) {
        this.resourceSet = resourceSet;
    }

    public LinkedHashMap<String, RawHealthCheck> getHealthChecks() {
        return healthChecks;
    }

    @JsonProperty("health-checks")
    public void setHealthChecks(LinkedHashMap<String, RawHealthCheck> healthChecks) {
        this.healthChecks = healthChecks;
    }

    public String getGoal() {
        return goal;
    }

    @JsonProperty("goal")
    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getCmd() {
        return cmd;
    }

    @JsonProperty("cmd")
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getImage() {
        return image;
    }

    @JsonProperty("image")
    public void setImage(String image) {
        this.image = image;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    @JsonProperty("env")
    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public Collection<String> getUris() {
        return CollectionUtils.isEmpty(uris) ? Collections.emptyList() : uris;
    }

    @JsonProperty("uris")
    public void setUris(Collection<String> uris) {
        this.uris = uris;
    }

    public Collection<RawPort> getPorts() {
        return ports;
    }

    @JsonProperty("ports")
    public void setPorts(Collection<RawPort> ports) {
        this.ports = ports;
    }

    public Collection<RawConfiguration> getConfigurations() {
        return configurations;
    }

    @JsonProperty("configurations")
    public void setConfigurations(Collection<RawConfiguration> configurations) {
        this.configurations = configurations;
    }

    public Collection<RawVolume> getVolumes() {
        return volumes;
    }

    @JsonProperty("volumes")
    public void setVolumes(Collection<RawVolume> volumes) {
        this.volumes = volumes;
    }
}

class RawConfiguration {
    private String template;
    private String dest;

    public String getTemplate() {
        return template;
    }

    @JsonProperty("template")
    public void setTemplate(String template) {
        this.template = template;
    }

    public String getDest() {
        return dest;
    }

    @JsonProperty("dest")
    public void setDest(String dest) {
        this.dest = dest;
    }
}

class RawResource {
    private String name;
    private String value;
    private String envKey;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    @JsonProperty("value")
    public void setValue(String value) {
        this.value = value;
    }

    public String getEnvKey() {
        return envKey;
    }

    @JsonProperty("env-key")
    public void setEnvKey(String envKey) {
        this.envKey = envKey;
    }
}

class RawVolume {
    String path;
    String type;
    int size;

    public String getPath() {
        return path;
    }

    @JsonProperty("path")
    public void setPath(String path) {
        this.path = path;
    }

    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    @JsonProperty("size")
    public void setSize(int size) {
        this.size = size;
    }
}

class RawPort {
    private String name;
    private Integer port;
    private RawVip vip;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public Integer getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(Integer port) {
        this.port = port;
    }

    public RawVip getVip() {
        return vip;
    }

    @JsonProperty("vip")
    public void setVip(RawVip vip) {
        this.vip = vip;
    }
}

class RawVip {
    int port;
    String prefix;

    public int getPort() {
        return port;
    }

    @JsonProperty("port")
    public void setPort(int port) {
        this.port = port;
    }

    public String getPrefix() {
        return prefix;
    }

    @JsonProperty("prefix")
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}

class RawHealthCheck {
    String name;
    String cmd;
    Duration interval;
    Duration gracePeriod;
    Duration delay;
    Duration timeout;
    Integer maxConsecutiveFailures;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getCmd() {
        return cmd;
    }

    @JsonProperty("cmd")
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public Duration getInterval() {
        return interval;
    }

    @JsonProperty("interval")
    public void setInterval(long intervalSecs) {
        this.interval = Duration.ofSeconds(intervalSecs);
    }

    public Duration getGracePeriod() {
        return gracePeriod;
    }

    @JsonProperty("grace-period")
    public void setGracePeriod(long gracePeriodSecs) {
        this.gracePeriod = Duration.ofSeconds(gracePeriodSecs);
    }

    public Integer getMaxConsecutiveFailures() {
        return maxConsecutiveFailures;
    }

    @JsonProperty("max-consecutive-failures")
    public void setMaxConsecutiveFailures(Integer maxConsecutiveFailures) {
        this.maxConsecutiveFailures = maxConsecutiveFailures;
    }

    public Duration getDelay() {
        return delay;
    }

    @JsonProperty("delay")
    public void setDelay(long delaySecs) {
        this.delay = Duration.ofSeconds(delaySecs);
    }

    public Duration getTimeout() {
        return timeout;
    }

    @JsonProperty("timeout")
    public void setTimeout(long timeoutSecs) {
        this.timeout = Duration.ofSeconds(timeoutSecs);
    }
}

class RawResourceSet {
    String id;
    Collection<RawResource> resources;
    Collection<RawVolume> volumes;
    Collection<RawVip> vips;

    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    public Collection<RawResource> getResources() {
        return resources;
    }

    @JsonProperty("resources")
    public void setResources(Collection<RawResource> resources) {
        this.resources = resources;
    }

    public Collection<RawVolume> getVolumes() {
        return volumes;
    }

    @JsonProperty("volumes")
    public void setVolumes(Collection<RawVolume> volumes) {
        this.volumes = volumes;
    }

    public Collection<RawVip> getVips() {
        return vips;
    }

    @JsonProperty("vips")
    public void setVips(Collection<RawVip> vips) {
        this.vips = vips;
    }
}


class RawPlan {
    private String name;
    private String strategy;
    private List<RawPhase> phases;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getStrategy() {
        return strategy;
    }

    @JsonProperty("strategy")
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<RawPhase> getPhases() {
        return phases;
    }

    @JsonProperty("phases")
    public void setPhases(List<RawPhase> phases) {
        this.phases = phases;
    }
}

class RawPhase {
    private String name;
    private String strategy;
    private String pod;
    private List<String> steps;

    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    public String getStrategy() {
        return strategy;
    }

    @JsonProperty("strategy")
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<String> getSteps() {
        return steps;
    }

    @JsonProperty("steps")
    public void setSteps(List<String> steps) {
        this.steps = steps;
    }

    public String getPod() {
        return pod;
    }

    @JsonProperty("pod")
    public void setPod(String pod) {
        this.pod = pod;
    }
}

