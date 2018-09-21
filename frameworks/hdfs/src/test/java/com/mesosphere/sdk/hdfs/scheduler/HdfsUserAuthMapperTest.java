package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.TaskEnvRouter;
import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.specification.yaml.TemplateUtils;
import com.mesosphere.sdk.testing.CosmosRenderer;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class HdfsUserAuthMapperTest {
    private static final String primary = "primary";
    private static final String frameworkHost = "host";
    private static final String realm = "realm";
    private static final String frameworkUser = "user";

    @Test
    public void testAddUserAuthMappingFromEnv() {
        Map<String, String> env = new HashMap<>();
        String expected = "value";
        env.put("key", Base64.getEncoder().encodeToString(expected.getBytes(StandardCharsets.UTF_8)));
        HdfsUserAuthMapper mapper = new HdfsUserAuthMapper(primary, frameworkHost, realm, frameworkUser);
        mapper.addUserAuthMappingFromEnv(env, "key");
        Assert.assertEquals(expected, mapper.getUserAuthMappingString());
    }

    @Test
    public void testAddDefaultUserAuthMapping() {
        HdfsUserAuthMapper mapper = new HdfsUserAuthMapper(primary, frameworkHost, realm, frameworkUser);
        mapper.addDefaultUserAuthMapping("data", 1);
        String expected = "RULE:[2:$1/$2@$0](primary/data-0-node.host@realm)s/.*/user/";
        Assert.assertEquals(expected, mapper.getUserAuthMappingString());
    }

    @Test
    public void testMultipleDefaultUserAuthMappings() {
        HdfsUserAuthMapper mapper = new HdfsUserAuthMapper(primary, frameworkHost, realm, frameworkUser);
        mapper.addDefaultUserAuthMapping("data", 2);
        String expected = "RULE:[2:$1/$2@$0](primary/data-0-node.host@realm)s/.*/user/\nRULE:[2:$1/$2@$0](primary/data-1-node.host@realm)s/.*/user/";
        Assert.assertEquals(expected, mapper.getUserAuthMappingString());
    }

    @Test
    public void testUserAuthMappingSeparation() {
        HdfsUserAuthMapper mapper = new HdfsUserAuthMapper(primary, frameworkHost, realm, frameworkUser);
        mapper.addDefaultUserAuthMapping("data", 1);
        Map<String, String> env = new HashMap<>();
        String expected = "value";
        env.put("key", Base64.getEncoder().encodeToString(expected.getBytes(StandardCharsets.UTF_8)));
        mapper.addUserAuthMappingFromEnv(env, "key");
        Assert.assertEquals(2, mapper.getUserAuthMappingString().split("\n").length);
    }

    @Test
    public void testAuthMapperTemplating() throws Exception {
        File template = ServiceTestRunner.getDistFile(Main.CORE_SITE_XML);
        String templateContent = new String(Files.readAllBytes(template.toPath()), StandardCharsets.UTF_8);

        Map<String, String> schedulerEnv =
                CosmosRenderer.renderSchedulerEnvironment(Collections.emptyMap(), Collections.emptyMap());
        Map<String, String> taskEnv = new TaskEnvRouter(schedulerEnv).getConfig("ALL");
        taskEnv.put(EnvConstants.FRAMEWORK_HOST_TASKENV, "hdfs.on.some.cluster");
        taskEnv.put("MESOS_SANDBOX", "/mnt/sandbox");
        taskEnv.put(Main.SERVICE_ZK_ROOT_TASKENV, "/dcos-service-path__to__hdfs");
        taskEnv.put("SECURITY_KERBEROS_ENABLED", "true");
        taskEnv.put("SECURITY_KERBEROS_PRIMARY", primary);
        taskEnv.put("SECURITY_KERBEROS_REALM", realm);
        taskEnv.put("SECURITY_KERBEROS_PRIMARY_HTTP", "primHttp");
        taskEnv.put("SCHEDULER_API_HOSTNAME", "schedulerHostname");

        HdfsUserAuthMapper mapper = new HdfsUserAuthMapper(primary, frameworkHost, realm, frameworkUser);
        mapper.addDefaultUserAuthMapping("data", 2);

        taskEnv.put("DECODED_AUTH_TO_LOCAL", mapper.getUserAuthMappingString());

        String filledTemplate = TemplateUtils.renderMustacheThrowIfMissing(template.getName(), templateContent, taskEnv);
        Assert.assertEquals(findAuthMappings(filledTemplate), mapper.getUserAuthMappingString());
    }

    private static String findAuthMappings(String config) throws IOException {
        BufferedReader bufReader = new BufferedReader(new StringReader(config));
        String line;
        boolean started = false;

        ArrayList<String> mappings = new ArrayList<>();
        while ((line=bufReader.readLine()) != null) {
            line = line.trim();
            if (line.equals("<name>hadoop.security.auth_to_local</name>")) {
                started = true;
                continue;
            }
            if (!started || line.startsWith("<value")) {
                continue;
            }
            if (line.startsWith("</")) {
                break;
            }
            mappings.add(line);
        }
        return String.join("\n", mappings);

    }
}
