package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.DiscoverySpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.TaskSpec;
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

    private static final String AUTOIP_TLD = "autoip.tld";
    private static final String VIP_TLD = "vip.tld";
    private static final String POD_NAME = "some-pod";

    @Mock private SchedulerConfig mockSchedulerConfig;
    @Mock private PodInstance mockPodInstance;
    @Mock private TaskSpec mockTaskSpec;
    @Mock private ResourceSet mockResourceSet;
    @Mock private DiscoverySpec mockDiscoverySpec;
    @Mock private NamedVIPSpec mockVIPSpec;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        Mockito.when(mockSchedulerConfig.getAutoipTLD()).thenReturn(AUTOIP_TLD);
        Mockito.when(mockSchedulerConfig.getVipTLD()).thenReturn(VIP_TLD);

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
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance, mockSchedulerConfig);
        RDN[] cnRDNs = certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN);
        Assert.assertEquals(cnRDNs.length, 1);
        Assert.assertEquals(String.format("%s-%s.%s", POD_NAME, TestConstants.TASK_NAME, TestConstants.SERVICE_NAME),
                cnRDNs[0].getFirst().getValue().toString());
    }

    @Test
    public void testGetSubjectWithLongCN() throws Exception {
        Mockito.when(mockTaskSpec.getName()).thenReturn(UUID.randomUUID().toString());
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(UUID.randomUUID().toString(), mockTaskSpec, mockPodInstance, mockSchedulerConfig);
        RDN[] cnRDNs = certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN);
        Assert.assertEquals(cnRDNs.length, 1);
        Assert.assertEquals(64, cnRDNs[0].getFirst().getValue().toString().length());
    }

    @Test
    public void testGetSANs() throws Exception {
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance, mockSchedulerConfig);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(1, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(taskDnsName(TestConstants.TASK_NAME, TestConstants.SERVICE_NAME)));
        Assert.assertFalse(names.contains(taskDnsName("*", TestConstants.SERVICE_NAME)));
        Assert.assertFalse(names.contains(taskVipName("*", TestConstants.SERVICE_NAME)));

        // Expected sha1sum of the hostname:
        byte[] digest = SHA1_HASHER.digest("some-pod-test-task-name.service-name.autoip.tld".getBytes(StandardCharsets.UTF_8));
        String sansHash = new String(Hex.encode(digest), StandardCharsets.UTF_8);

        Assert.assertEquals(sansHash, certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testSlashesInServiceName() throws Exception {
        String serviceNameWithSlashes = "service/name/with/slashes";
        String serviceNameWithoutSlashes = "servicenamewithslashes";

        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(serviceNameWithSlashes, mockTaskSpec, mockPodInstance, mockSchedulerConfig);

        Assert.assertEquals(String.format("%s-%s.%s", POD_NAME, TestConstants.TASK_NAME, serviceNameWithoutSlashes),
                certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());

        List<String> names = Arrays.stream(certificateNamesGenerator.getSANs().getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(taskDnsName(TestConstants.TASK_NAME, serviceNameWithoutSlashes)));
        Assert.assertFalse(names.contains(taskDnsName("*", serviceNameWithoutSlashes)));
        Assert.assertFalse(names.contains(taskVipName("*", serviceNameWithoutSlashes)));

        // Expected sha1sum of the hostname:
        byte[] digest = SHA1_HASHER.digest("some-pod-test-task-name.servicenamewithslashes.autoip.tld".getBytes(StandardCharsets.UTF_8));
        String sansHash = new String(Hex.encode(digest), StandardCharsets.UTF_8);

        Assert.assertEquals(sansHash, certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testDiscoveryNameAddedAsSan() {
        Mockito.when(mockTaskSpec.getDiscovery()).thenReturn(Optional.of(mockDiscoverySpec));
        Mockito.when(mockDiscoverySpec.getPrefix()).thenReturn(Optional.of("custom-name"));
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance, mockSchedulerConfig);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(1, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(String.format("custom-name-0.%s.%s", TestConstants.SERVICE_NAME, AUTOIP_TLD)));

        // Expected sha1sum of the hostname:
        byte[] digest = SHA1_HASHER.digest("custom-name-0.service-name.autoip.tld".getBytes(StandardCharsets.UTF_8));
        String sansHash = new String(Hex.encode(digest), StandardCharsets.UTF_8);

        Assert.assertEquals(sansHash, certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testVipsAddedAsSans() {
        Mockito.when(mockResourceSet.getResources()).thenReturn(Collections.singletonList(mockVIPSpec));
        Mockito.when(mockVIPSpec.getVipName()).thenReturn("test-vip");
        Mockito.when(mockVIPSpec.getPort()).thenReturn(8000L);
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance, mockSchedulerConfig);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(2, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(2, names.size());
        Assert.assertTrue(names.contains(taskDnsName(TestConstants.TASK_NAME, TestConstants.SERVICE_NAME)));
        Assert.assertTrue(names.contains(taskVipName("test-vip", TestConstants.SERVICE_NAME)));

        // Expected sha1sum of the hostname:
        byte[] digest = SHA1_HASHER.digest(
                "some-pod-test-task-name.service-name.autoip.tld;test-vip.service-name.vip.tld"
                .getBytes(StandardCharsets.UTF_8));
        String sansHash = new String(Hex.encode(digest), StandardCharsets.UTF_8);

        Assert.assertEquals(sansHash, certificateNamesGenerator.getSANsHash());
    }

    private static String taskDnsName(String taskName, String serviceName) {
         return String.format("%s-%s.%s.%s", POD_NAME, taskName, serviceName, AUTOIP_TLD);
    }

    private static String taskVipName(String vipName, String serviceName) {
        return String.format("%s.%s.%s", vipName, serviceName, VIP_TLD);
    }
}
