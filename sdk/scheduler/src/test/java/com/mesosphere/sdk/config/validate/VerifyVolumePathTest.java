package com.mesosphere.sdk.config.validate;
import com.mesosphere.sdk.specification.*;
import org.junit.Test;

public class VerifyVolumePathTest {

    private static final int DISK_SIZE_MB = 1000;

    /*
    If Pattern is used in DefaultVolumeSpec,  exception is ValidationException
    Without Pattern,  exception is ConstraintViolationException
            using Exception.class to be safe in case VolumeSpec adds/removes fields
    */
    @Test(expected = Exception.class)
    public void testVolumePathEmpty() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.MOUNT, "", "role", "*", "principal");
    }

    @Test(expected = Exception.class)
    public void testVolumePathBlank() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.MOUNT, " ", "role", "*", "principal");
    }

    @Test(expected = Exception.class)
    public void testVolumePathSlash() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.MOUNT, "/path/to/volume0", "role", "*", "principal");
    }

    @Test(expected = Exception.class)
    public void testVolumePathChar() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.MOUNT, "@?test", "role", "*", "principal");
    }

    @Test(expected = Exception.class)
    public void testVolumePathBeg() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.MOUNT, "-test", "role", "*", "principal");
    }

    @Test
    public void testVolumePathNumber() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.ROOT, "path-0_1-path", "role", "*", "principal");
    }

    @Test
    public void testVolumePathCorrect0() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.ROOT, "path", "role", "*", "principal");
    }


    @Test(expected = Exception.class)
    public void testVolumePathSlash1() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.ROOT, "path/path", "role", "*", "principal");
    }

    @Test(expected = Exception.class)
    public void testVolumePathSlash2() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB, VolumeSpec.Type.ROOT, "path-0/1-path", "role", "*", "principal");
    }
}
