package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.DefaultHostVolumeSpec;
import org.junit.Test;

public class VerifyHostVolumePathTest {

    /* Exception.class is generic (in case we add/change fields)
           If Pattern is used in DefaultHostVolumeSpec,  exception is ValidationException
    */

    @Test(expected = Exception.class)
    public void testHostPathEmpty() {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath("")
                .containerPath("etc")
                .build();
    }

    @Test(expected = Exception.class)
    public void testContainerPathEmpty() {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath("/etc")
                .containerPath("")
                .build();
    }

    @Test(expected = Exception.class)
    public void testHostPathBlank()  {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath(" ")
                .containerPath("etc")
                .build();
    }

    @Test(expected = Exception.class)
    public void testContainerPathBlank()  {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath("/etc")
                .containerPath(" ")
                .build();
    }

    @Test(expected = Exception.class)
    public void testContainerPathStartWithSlash()  {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath("/etc")
                .containerPath("/etc")
                .build();
    }

    @Test
    public void testHostPathStartWithSlash()  {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath("/etc")
                .containerPath("etc")
                .build();
    }

    @Test
    public void testContainerPathContainsSlash()  {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath("/etc/abc")
                .containerPath("etc/abc")
                .build();
    }

    @Test(expected = Exception.class)
    public void testHostPathStartWithDot()  {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath("./etc")
                .containerPath("etc")
                .build();
    }

    @Test(expected = Exception.class)
    public void testMissingContainerPath()  {
        DefaultHostVolumeSpec.newBuilder()
                .containerPath("etc")
                .build();
    }
    @Test(expected = Exception.class)
    public void testMissingHostPath()  {
        DefaultHostVolumeSpec.newBuilder()
                .hostPath("/etc")
                .build();
    }
}
