package com.mesosphere.sdk.config.validate;
import com.mesosphere.sdk.specification.*;
import org.junit.Test;
import org.junit.Assert;

import java.util.Map;

public class DockerVolumeSpecTest {

    private static final double DISK_SIZE_MB = 1000;

    @Test
    public void testDockerVolumeNoOptions() {
        new DockerVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.DOCKER, "testVolume", "pxd", "", "testPath", "role", "", "principal");
    }

    @Test(expected = Exception.class)
    public void testDockerVolumeNoDriverName() {
        new DockerVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.DOCKER, "testVolume", "", "", "testPath", "role", "", "principal");
    }

    @Test(expected = Exception.class)
    public void testDockerVolumeNoVolumeName() {
        new DockerVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.DOCKER, "", "pxd", "", "testPath", "role", "", "principal");
    }

    @Test
    public void testDockerVolumeOptions() {
        DockerVolumeSpec spec = new DockerVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.DOCKER, "testVolume", "pxd", "key1=value1,key2=value2,key3", "testPath",
                "role", "", "principal");
        Map<String, String> options = spec.getDriverOptions();
        Assert.assertTrue(options.containsKey("key1"));
        String value = options.get("key1");
        Assert.assertEquals("value1", value);
        Assert.assertTrue(options.containsKey("key2"));
        value = options.get("key2");
        Assert.assertEquals("value2", value);
        Assert.assertFalse(options.containsKey("key3"));
    }
}
