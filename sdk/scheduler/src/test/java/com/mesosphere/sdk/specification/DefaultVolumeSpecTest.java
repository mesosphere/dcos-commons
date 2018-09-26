package com.mesosphere.sdk.specification;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class DefaultVolumeSpecTest {

    private static final int DISK_SIZE_MB = 1000;

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathEmpty() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathBlank() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                " ",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathSlash() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "/path/to/volume0",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathChar() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "@?test",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathBeg() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "-test",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testVolumePathNumber() {
        DefaultVolumeSpec.createRootVolume(
                DISK_SIZE_MB,
                "path-0_1-path",
                "role",
                "*",
                "principal");
    }

    @Test
    public void testVolumePathCorrect0() {
        DefaultVolumeSpec.createRootVolume(
                DISK_SIZE_MB,
                "path",
                "role",
                "*",
                "principal");
    }


    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathSlash1() {
        DefaultVolumeSpec.createRootVolume(
                DISK_SIZE_MB,
                "path/path",
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVolumePathSlash2() {
        DefaultVolumeSpec.createRootVolume(
                DISK_SIZE_MB,
                "path-0/1-path",
                "role",
                "*",
                "principal");
    }

    @Test
    public void testMountVolumeEmptyProfiles() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Collections.emptyList(),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testMountVolumeProfiles() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test"),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesNull() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", null),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesEmpty() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", ""),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesBlank() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", " "),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testMountVolumeProfilesNumber() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", "0"),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesSlash() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", "a/b"),
                "role",
                "*",
                "principal");
    }

    @Test
    public void testMountVolumeProfilesValidChars() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", "_.-"),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesInvalidChars() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", "@?"),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesLong() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", new String(new char[129]).replace("\0", "a")),
                "role",
                "*",
                "principal");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMountVolumeProfilesDuplicate() {
        DefaultVolumeSpec.createMountVolume(
                DISK_SIZE_MB,
                "path",
                Arrays.asList("test", "test"),
                "role",
                "*",
                "principal");
    }
}
