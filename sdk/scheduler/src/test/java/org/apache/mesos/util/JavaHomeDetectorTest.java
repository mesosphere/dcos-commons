package org.apache.mesos.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JavaHomeDetectorTest {
    @Test
    public void testEnvHasJavaHomeFalse() {
        Assert.assertFalse(JavaHomeDetector.envHasJavaHome(Collections.emptyMap()));
    }

    @Test
    public void testEnvHasJavaHomeTrue() {
        Map<String, String> envMap = new HashMap<>();
        envMap.put("JAVA_HOME", JavaHomeDetector.DEFAULT_JAVA_HOME);
        Assert.assertTrue(JavaHomeDetector.envHasJavaHome(envMap));
    }

    @Test
    public void testGetJavaHomeFromEnvDefault() {
        Assert.assertEquals(JavaHomeDetector.DEFAULT_JAVA_HOME,
                JavaHomeDetector.getJavaHomeFromEnv(Collections.emptyMap()));
    }

    @Test
    public void testGetJavaHomeFromEnv() {
        Map<String, String> envMap = new HashMap<>();
        final String fooBar = "FooBar";
        envMap.put("JAVA_HOME", fooBar);
        Assert.assertEquals(fooBar, JavaHomeDetector.getJavaHomeFromEnv(envMap));
    }
}
