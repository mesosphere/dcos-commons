package org.apache.mesos.specification.yaml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FileUtils;
import org.apache.mesos.offer.constrain.AndRule;
import org.apache.mesos.offer.constrain.TaskTypeRule;
import org.apache.mesos.scheduler.SchedulerUtils;
import org.apache.mesos.specification.DefaultPodSpec;
import org.apache.mesos.specification.DefaultServiceSpec;
import org.apache.mesos.specification.PodSpec;
import org.apache.mesos.specification.ServiceSpec;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Generates {@link ServiceSpec} from a given YAML definition.
 */
public class YAMLServiceSpecFactory {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static final ServiceSpec generateFromYAML(File pathToYaml) throws IOException {
        return generateFromYAML(FileUtils.readFileToString(pathToYaml, Charset.forName("UTF-8")));
    }

    public static final ServiceSpec generateFromYAML(String yaml) throws IOException {
        RawServiceSpecification rawSvcSpec = YAML_MAPPER.readValue(yaml.getBytes(), RawServiceSpecification.class);

        return DefaultServiceSpec.Builder.newBuilder()
                .name(rawSvcSpec.getName())
                .apiPort(rawSvcSpec.getApiPort())
                .principal(rawSvcSpec.getPrincipal())
                .zookeeperConnection(rawSvcSpec.getZookeeper())
                .pods(generatePodSpecs(rawSvcSpec))
                .role(SchedulerUtils.nameToRole(rawSvcSpec.getName()))
                .build();
    }

    private static final List<PodSpec> generatePodSpecs(RawServiceSpecification rawServiceSpecification) {
        final LinkedHashMap<String, RawPod> rawPods = rawServiceSpecification.getPods();
        List<PodSpec> pods = new LinkedList<>();

        for (Map.Entry<String, RawPod> entry : rawPods.entrySet()) {
            pods.add(generatePodSpec(entry.getKey(), entry.getValue()));
        }

        return pods;
    }

    private static final PodSpec generatePodSpec(String podName, RawPod rawPod) {
        return DefaultPodSpec.Builder.newBuilder()
                .type(podName)
                .count(rawPod.getCount())
                .placementRule(null)
                .resources(null).build();
    }
}
