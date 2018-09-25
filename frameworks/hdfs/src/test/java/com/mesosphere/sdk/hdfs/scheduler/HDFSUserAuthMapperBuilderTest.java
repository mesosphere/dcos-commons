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

public class HDFSUserAuthMapperBuilderTest {
    private static final String frameworkHost = "host";
    private static final Map<String, String> ENV;
    static {
        ENV = new HashMap<>();
        ENV.put(HDFSAuthEnvContainer.PRIMARY_ENV_KEY, "primary");
        ENV.put(HDFSAuthEnvContainer.FRAMEWORK_USER_ENV_KEY, "user");
        ENV.put(HDFSAuthEnvContainer.REALM_ENV_KEY, "realm");
    }


    @Test(expected = RuntimeException.class)
    public void testThrowExceptionOnEnvWithoutAllRequiredKeys() {
        Map<String, String> env = new HashMap<>();
        new HDFSUserAuthMapperBuilder(env, frameworkHost);
    }

    @Test
    public void testAddUserAuthMappingFromEnv() {
        String expected = "value";
        Map<String, String> env = getEnvWithEncodedAuthToLocal(expected);
        String authMappings = new HDFSUserAuthMapperBuilder(env, frameworkHost)
                .addUserAuthMappingFromEnv()
                .build();
        Assert.assertEquals(expected, authMappings);
    }

    @Test
    public void testAddDefaultUserAuthMapping() {
        String authMappings = new HDFSUserAuthMapperBuilder(ENV, frameworkHost)
                .addDefaultUserAuthMapping("data", "node", 1)
                .build();
        String expected = "RULE:[2:$1/$2@$0](primary/data-0-node.host@realm)s/.*/user/";
        Assert.assertEquals(expected, authMappings);
    }

    @Test
    public void testMultipleDefaultUserAuthMappings() {
        String authMappings = new HDFSUserAuthMapperBuilder(ENV, frameworkHost)
                .addDefaultUserAuthMapping("data", "node", 2)
                .build();
        String expected = "RULE:[2:$1/$2@$0](primary/data-0-node.host@realm)s/.*/user/\nRULE:[2:$1/$2@$0](primary/data-1-node.host@realm)s/.*/user/";
        Assert.assertEquals(expected, authMappings);
    }

    @Test
    public void testUserAuthMappingSeparation() {
        String expected = "value";
        Map<String, String> env = getEnvWithEncodedAuthToLocal(expected);
        String authMappings = new HDFSUserAuthMapperBuilder(env, frameworkHost)
                .addUserAuthMappingFromEnv()
                .addDefaultUserAuthMapping("data", "node", 1)
                .build();
        Assert.assertEquals(2, authMappings.split("\n").length);
    }

    @Test
    public void testFilterEmptyAuthMappingLines() {
        Map<String, String> env = getEnvWithEncodedAuthToLocal("");
        String authMappings = new HDFSUserAuthMapperBuilder(env, frameworkHost)
                .addUserAuthMappingFromEnv()
                .addUserAuthMappingFromEnv()
                .build();
        Assert.assertEquals(1, authMappings.split("\n").length);
    }

    @Test
    public void testAuthMapperTemplating() throws Exception {
        File template = ServiceTestRunner.getDistFile(Main.CORE_SITE_XML);
        String templateContent = new String(Files.readAllBytes(template.toPath()), StandardCharsets.UTF_8);

        String realm = ENV.get(HDFSAuthEnvContainer.REALM_ENV_KEY);
        String primary = ENV.get(HDFSAuthEnvContainer.PRIMARY_ENV_KEY);

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

        String authMappings = new HDFSUserAuthMapperBuilder(ENV, frameworkHost)
                .addDefaultUserAuthMapping("data", "node", 2)
                .build();

        taskEnv.put("DECODED_AUTH_TO_LOCAL", authMappings);


        String filledTemplate = TemplateUtils.renderMustacheThrowIfMissing(template.getName(), templateContent, taskEnv);
        Assert.assertEquals(findAuthMappings(filledTemplate), authMappings);
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

    private static Map<String, String> getEnvWithEncodedAuthToLocal(String authToLocal) {
        Map<String, String> env = new HashMap<>();
        env.put(HDFSAuthEnvContainer.TASKCFG_ALL_AUTH_TO_LOCAL, Base64.getEncoder()
                .encodeToString(authToLocal.getBytes(StandardCharsets.UTF_8)));
        env.putAll(ENV);
        return env;
    }


}
