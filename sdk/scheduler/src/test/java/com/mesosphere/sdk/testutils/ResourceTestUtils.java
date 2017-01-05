package com.mesosphere.sdk.testutils;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import com.mesosphere.sdk.offer.ResourceUtils;

import java.util.Arrays;

public class ResourceTestUtils {
    public static Resource getDesiredRootVolume(double diskSize) {
        return ResourceUtils.getDesiredRootVolume(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                diskSize,
                TestConstants.CONTAINER_PATH);
    }

    public static Resource getDesiredMountVolume(double diskSize) {
        return ResourceUtils.getDesiredMountVolume(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                diskSize,
                TestConstants.CONTAINER_PATH);
    }

    public static Resource getUnreservedMountVolume(double diskSize) {
        return ResourceUtils.getUnreservedMountVolume(diskSize, TestConstants.MOUNT_ROOT);
    }

    public static Resource getExpectedMountVolume(double diskSize) {
        return getExpectedMountVolume(diskSize, TestConstants.RESOURCE_ID);
    }

    public static Resource getExpectedMountVolume(double diskSize, String resourceId) {
        return ResourceUtils.getExpectedMountVolume(
                diskSize,
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.MOUNT_ROOT,
                TestConstants.CONTAINER_PATH,
                TestConstants.PERSISTENCE_ID);
    }

    public static Resource getExpectedRootVolume(double diskSize) {
        return getExpectedRootVolume(diskSize, TestConstants.RESOURCE_ID);
    }

    public static Resource getExpectedRootVolume(double diskSize, String resourceId) {
        return ResourceUtils.getExpectedRootVolume(
                diskSize,
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.PERSISTENCE_ID);
    }

    public static Resource getDesiredScalar(String name, double value) {
        return ResourceUtils.getDesiredScalar(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                name,
                value);
    }

    public static Resource getExpectedScalar(String name, double value, String resourceId) {
        return ResourceUtils.getExpectedScalar(
                name,
                value,
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
    }

    public static final Resource getExpectedRanges(String name, long begin, long end, String resourceId) {
        return ResourceUtils.getExpectedRanges(
                name,
                Arrays.asList(Protos.Value.Range.newBuilder().setBegin(begin).setEnd(end).build()),
                resourceId,
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);
    }

    public static final Resource getDesiredRanges(String name, long begin, long end) {
        return ResourceUtils.getDesiredRanges(
                TestConstants.ROLE,
                TestConstants.PRINCIPAL,
                name,
                Arrays.asList(Protos.Value.Range.newBuilder().setBegin(begin).setEnd(end).build()));
    }

    public static Resource getUnreservedCpu(double cpus) {
        return ResourceUtils.getUnreservedScalar("cpus", cpus);
    }

    public static Resource getUnreservedMem(double mem) {
        return ResourceUtils.getUnreservedScalar("mem", mem);
    }

    public static Resource getUnreservedDisk(double disk) {
        return ResourceUtils.getUnreservedScalar("disk", disk);
    }

    public static Resource getUnreservedPorts(int begin, int end) {
        return ResourceUtils.getUnreservedRanges(
                "ports",
                Arrays.asList(Protos.Value.Range.newBuilder()
                        .setBegin(begin)
                        .setEnd(end)
                        .build()));
    }

    public static Resource getExpectedCpu(double cpus) {
        return ResourceTestUtils.getExpectedScalar("cpus", cpus, TestConstants.RESOURCE_ID);
    }

    public static Resource getDesiredCpu(double cpus) {
        return ResourceTestUtils.getDesiredScalar("cpus", cpus);
    }

    public static Resource getDesiredMem(double mem) {
        return ResourceTestUtils.getDesiredScalar("mem", mem);
    }

}
