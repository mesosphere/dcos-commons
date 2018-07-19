package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.DiscoverySpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

public class CertificateNamesGeneratorTest {

    private static final MessageDigest SHA1_HASHER;
    static {
        try {
            SHA1_HASHER = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();
    private static final String POD_NAME = "some-pod";

    @Mock private PodInstance mockPodInstance;
    @Mock private TaskSpec mockTaskSpec;
    @Mock private ResourceSet mockResourceSet;
    @Mock private DiscoverySpec mockDiscoverySpec;
    @Mock private NamedVIPSpec mockVIPSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(mockPodInstance.getIndex()).thenReturn(0);
        Mockito.when(mockPodInstance.getName()).thenReturn(POD_NAME);

        Mockito.when(mockTaskSpec.getName()).thenReturn(TestConstants.TASK_NAME);
        Mockito.when(mockTaskSpec.getDiscovery()).thenReturn(Optional.empty());
        Mockito.when(mockTaskSpec.getResourceSet()).thenReturn(mockResourceSet);

        Mockito.when(mockResourceSet.getResources()).thenReturn(Collections.emptyList());
    }

    @Test
    public void testGetSubject() throws Exception {
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance, SCHEDULER_CONFIG);
        RDN[] cnRDNs = certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN);
        Assert.assertEquals(cnRDNs.length, 1);
        Assert.assertEquals(String.format("%s-%s.%s", POD_NAME, TestConstants.TASK_NAME, TestConstants.SERVICE_NAME),
                cnRDNs[0].getFirst().getValue().toString());
    }

    @Test
    public void testGetSubjectWithLongCN() throws Exception {
        Mockito.when(mockTaskSpec.getName()).thenReturn(UUID.randomUUID().toString());
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(UUID.randomUUID().toString(), mockTaskSpec, mockPodInstance, SCHEDULER_CONFIG);
        RDN[] cnRDNs = certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN);
        Assert.assertEquals(cnRDNs.length, 1);
        Assert.assertEquals(64, cnRDNs[0].getFirst().getValue().toString().length());
    }

    @Test
    public void testGetSANs() throws Exception {
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance, SCHEDULER_CONFIG);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(1, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.toString(), names.contains(taskDnsName(TestConstants.TASK_NAME, TestConstants.SERVICE_NAME)));
        Assert.assertFalse(names.contains(taskDnsName("*", TestConstants.SERVICE_NAME)));
        Assert.assertFalse(names.contains(taskVipName("*", TestConstants.SERVICE_NAME)));

        Assert.assertEquals(
                toSansHash("some-pod-test-task-name.service-name." + SCHEDULER_CONFIG.getAutoipTLD()),
                certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testSlashesInServiceName() throws Exception {
        String serviceNameWithSlashes = "service/name/with/slashes";
        String serviceNameWithoutSlashes = "servicenamewithslashes";

        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(serviceNameWithSlashes, mockTaskSpec, mockPodInstance, SCHEDULER_CONFIG);

        Assert.assertEquals(String.format("%s-%s.%s", POD_NAME, TestConstants.TASK_NAME, serviceNameWithoutSlashes),
                certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());

        List<String> names = Arrays.stream(certificateNamesGenerator.getSANs().getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.toString(), names.contains(taskDnsName(TestConstants.TASK_NAME, serviceNameWithoutSlashes)));
        Assert.assertFalse(names.contains(taskDnsName("*", serviceNameWithoutSlashes)));
        Assert.assertFalse(names.contains(taskVipName("*", serviceNameWithoutSlashes)));

        Assert.assertEquals(
                toSansHash("some-pod-test-task-name.servicenamewithslashes." + SCHEDULER_CONFIG.getAutoipTLD()),
                certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testDiscoveryNameAddedAsSan() {
        Mockito.when(mockTaskSpec.getDiscovery()).thenReturn(Optional.of(mockDiscoverySpec));
        Mockito.when(mockDiscoverySpec.getPrefix()).thenReturn(Optional.of("custom-name"));
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance, SCHEDULER_CONFIG);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(1, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.toString(), names.contains(taskDnsName("custom", "name-0", TestConstants.SERVICE_NAME)));

        Assert.assertEquals(toSansHash("custom-name-0.service-name." + SCHEDULER_CONFIG.getAutoipTLD()), certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testVipsAddedAsSans() {
        Mockito.when(mockResourceSet.getResources()).thenReturn(Collections.singletonList(mockVIPSpec));
        Mockito.when(mockVIPSpec.getVipName()).thenReturn("test-vip");
        Mockito.when(mockVIPSpec.getPort()).thenReturn(8000L);
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance, SCHEDULER_CONFIG);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(2, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(2, names.size());
        Assert.assertTrue(names.toString(), names.contains(taskDnsName(TestConstants.TASK_NAME, TestConstants.SERVICE_NAME)));
        Assert.assertTrue(names.contains(taskVipName("test-vip", TestConstants.SERVICE_NAME)));

        Assert.assertEquals(
                toSansHash(
                        "some-pod-test-task-name.service-name." + SCHEDULER_CONFIG.getAutoipTLD() + ";" +
                        "test-vip.service-name." + SCHEDULER_CONFIG.getVipTLD()),
                certificateNamesGenerator.getSANsHash());
    }

    private static String toSansHash(String hostnamesString) {
        byte[] digest = SHA1_HASHER.digest(hostnamesString.getBytes(StandardCharsets.UTF_8));
        return new String(Hex.encode(digest), StandardCharsets.UTF_8);
    }

    private static String taskDnsName(String taskName, String serviceName) {
        return taskDnsName(POD_NAME, taskName, serviceName);
    }

    private static String taskDnsName(String podName, String taskName, String serviceName) {
        return String.format("%s-%s.%s.%s", podName, taskName, serviceName, SCHEDULER_CONFIG.getAutoipTLD());
    }

    private static String taskVipName(String vipName, String serviceName) {
        return String.format("%s.%s.%s", vipName, serviceName, SCHEDULER_CONFIG.getVipTLD());
    }
}
