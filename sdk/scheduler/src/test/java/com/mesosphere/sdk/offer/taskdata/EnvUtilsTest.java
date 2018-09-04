package com.mesosphere.sdk.offer.taskdata;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link EnvUtils}.
 */
public class EnvUtilsTest {

    private final static String SECRET_KEY = "SECRET_KEY";
    private final static String SECRET_PATH = "SECRET_PATH";
    private final static String TEST_ENV_KEY = "TEST_KEY";
    private final static String TEST_ENV_VALUE = "TEST_VALUE";
    private final static String TEST_REPLACE_ENV_VALUE = "TEST_NEW_VALUE";

    /*
        Make sure that withEnvVar does not accidentally strip secrets.
        DCOS-38256
     */
    @Test
    public void addEnvVarToEnvironmentWithSecret() {
        Protos.Environment.Builder envBuilder = Protos.Environment.newBuilder();
        envBuilder.addVariablesBuilder()
                .setName(SECRET_KEY)
                .setType(Protos.Environment.Variable.Type.SECRET)
                .setSecret(getReferenceSecret(SECRET_PATH));
        Protos.Environment newEnv = EnvUtils.withEnvVar(envBuilder.build(), TEST_ENV_KEY, TEST_ENV_VALUE);
        Assert.assertEquals(newEnv.getVariablesCount(), 2);
        for(Protos.Environment.Variable envVar : newEnv.getVariablesList()) {
            if (envVar.getName().equals(SECRET_KEY)) {
                Assert.assertEquals(envVar.getSecret(), getReferenceSecret(SECRET_PATH));
            }
            if (envVar.getName().equals(TEST_ENV_KEY)) {
                Assert.assertEquals(envVar.getValue(), TEST_ENV_VALUE);
            }

        }
    }

    /*
        Make sure withEnvVar overwrites an existing key's value, instead of adding a new one.
    */
    @Test
    public void overwriteExistingEnvVarKey() {
        Protos.Environment.Builder envBuilder = Protos.Environment.newBuilder();
        envBuilder.addVariablesBuilder()
                .setName(TEST_ENV_KEY)
                .setValue(TEST_ENV_VALUE)
                .build();
        Protos.Environment newEnv = EnvUtils.withEnvVar(envBuilder.build(), TEST_ENV_KEY, TEST_REPLACE_ENV_VALUE);
        Assert.assertEquals(newEnv.getVariablesCount(), 1);
        Protos.Environment.Variable envVar = newEnv.getVariables(0);
        Assert.assertEquals(envVar.getValue(), TEST_REPLACE_ENV_VALUE);
    }

    private static Protos.Secret getReferenceSecret(String secretPath) {
        return Protos.Secret.newBuilder()
                .setType(Protos.Secret.Type.REFERENCE)
                .setReference(Protos.Secret.Reference.newBuilder().setName(secretPath))
                .build();
    }
}
