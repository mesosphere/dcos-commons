package com.mesosphere.sdk.hdfs.scheduler;

import org.junit.Assert;
import org.junit.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class HdfsUserAuthMapperTest {
    private static final String primary = "primary";
    private static final String frameworkHost = "host";
    private static final String realm = "realm";
    private static final String frameworkUser = "user";

    @Test
    public void testAddUserAuthMappingFromEnv() {
        Map<String, String> env = new HashMap<>();
        String expected = "value";
        env.put("key", Base64.getEncoder().encodeToString(expected.getBytes()));
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
        env.put("key", Base64.getEncoder().encodeToString(expected.getBytes()));
        mapper.addUserAuthMappingFromEnv(env, "key");
        Assert.assertEquals(2, mapper.getUserAuthMappingString().split("\n").length);
    }

}
