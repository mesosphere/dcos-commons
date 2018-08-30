package com.mesosphere.sdk.specification;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class DefaultVolumeSpecTest {

    private static final int DISK_SIZE_MB = 1000;

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathEmpty() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathBlank() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                " ",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathSlash() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "/path/to/volume0",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathChar() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "@?test",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathBeg() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "-test",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testVolumePathNumber() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.ROOT,
                "path-0_1-path",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testVolumePathCorrect0() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.ROOT,
                "path",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }


    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathSlash1() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.ROOT,
                "path/path",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathSlash2() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.ROOT,
                "path-0/1-path",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRootVolumeProfiles() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.ROOT,
                "path",
                Arrays.asList("test"),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testPathVolumeEmptyProfiles() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.PATH,
                "path",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPathVolumeProfiles() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.PATH,
                "path",
                Arrays.asList("test"),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testMountVolumeEmptyProfiles() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testMountVolumeProfiles() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test"),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesNull() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", null),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesEmpty() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", ""),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesBlank() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", " "),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testMountVolumeProfilesNumber() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", "0"),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesSlash() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", "a/b"),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testMountVolumeProfilesValidChars() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", "_.-"),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesInvalidChars() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", "@?"),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesLong() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", new String(new char[129]).replace("\0", "a")),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesDuplicate() {
        new DefaultVolumeSpec(
                DISK_SIZE_MB,
                VolumeSpec.Type.MOUNT,
                "path",
                Arrays.asList("test", "test"),
                "role",
                "*",
                "principal");
    }
}
