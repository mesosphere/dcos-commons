package org.apache.mesos.specification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class YAMLServiceFactory {
    static String yaml = "name: \"${SERVICE_NAME}\"\n" +
            "principal: \"${SERVICE_PRINCIPAL}\"\n" +
            "pods:\n" +
            "  - name: pod-type-a\n" +
            "    placement: \"avoid-type: task-a\"\n" +
            "    count: 5\n" +
            "    tasks:\n" +
            "      - name: task-a\n" +
            "        goal: RUNNING\n" +
            "        cmd: \"echo hello\"\n" +
            "        image: ubuntu:16.04\n" +
            "        uris:\n" +
            "          - \"${ARTIFACT_1}\"\n" +
            "          - \"${ARTIFACT_2}\"\n" +
            "        env:\n" +
            "          TASK_A_ENV_KEY: \"${SCHEDULER_ENV_VALUE}\"\n" +
            "        cpus: 1.3\n" +
            "        memory: 1024\n" +
            "        ports:\n" +
            "          - name: http\n" +
            "            port: 8080\n" +
            "            vip:\n" +
            "              prefix: task-a\n" +
            "              port: 4040\n" +
            "          - name: admin\n" +
            "            port: 0\n" +
            "        configurations:\n" +
            "          - template: config.yaml.tmpl\n" +
            "            dest: /config/path/config.yaml\n" +
            "        health-checks:\n" +
            "          - name: check-up\n" +
            "            cmd: \"curl -f http://localhost:<port>/check-up\"\n" +
            "            interval: 5\n" +
            "            grace-period: 30\n" +
            "            max-consecutive-failures: 3\n" +
            "    volumes:\n" +
            "      - path: /data/path-foo\n" +
            "        type: MOUNT\n" +
            "        size: 500000\n" +
            "      - path: /data/path-tools\n" +
            "        type: MOUNT\n" +
            "        size: 5000\n" +
            "plans:\n" +
            "  - name: deploy\n" +
            "    strategy: parallel\n" +
            "    phases:\n" +
            "      - name: A-phase\n" +
            "        strategy: parallel\n" +
            "        pod: pod-type-a\n" +
            "        steps:\n" +
            "          - task-a\n" +
            "          - task-foo-a\n" +
            "      - name: B-phase\n" +
            "        strategy: serial\n" +
            "        pod: pod-type-b";

    public static void main(String[] args) throws Exception{
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        final RawServiceSpecification serviceSpec = mapper.readValue(yaml.getBytes(), RawServiceSpecification.class);
        System.out.println(serviceSpec);
    }
}
