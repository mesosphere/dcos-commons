package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.DiscoverySpec;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ResourceSet;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;

public class CertificateNamesGeneratorTest {

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
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance);
        RDN[] cnRDNs = certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN);
        Assert.assertEquals(cnRDNs.length, 1);
        Assert.assertEquals(String.format("%s-%s.%s", POD_NAME, TestConstants.TASK_NAME, TestConstants.SERVICE_NAME),
                cnRDNs[0].getFirst().getValue().toString());
    }

    @Test
    public void testGetSubjectWithLongCN() throws Exception {
        Mockito.when(mockTaskSpec.getName()).thenReturn(UUID.randomUUID().toString());
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(UUID.randomUUID().toString(), mockTaskSpec, mockPodInstance);
        RDN[] cnRDNs = certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN);
        Assert.assertEquals(cnRDNs.length, 1);
        Assert.assertEquals(64, cnRDNs[0].getFirst().getValue().toString().length());
    }

    @Test
    public void testGetSANs() throws Exception {
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(1, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(taskDnsName(TestConstants.TASK_NAME, TestConstants.SERVICE_NAME)));
        Assert.assertFalse(names.contains(taskDnsName("*", TestConstants.SERVICE_NAME)));
        Assert.assertFalse(names.contains(taskVipName("*", TestConstants.SERVICE_NAME)));
        // echo -n "some-pod-test-task-name.service-name.autoip.dcos.thisdcos.directory" | sha1sum
        Assert.assertEquals("a22fd2735aae7c55e47bece5f6c10612866583bf", certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testSlashesInServiceName() throws Exception {
        String serviceNameWithSlashes = "service/name/with/slashes";
        String serviceNameWithoutSlashes = "servicenamewithslashes";

        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(serviceNameWithSlashes, mockTaskSpec, mockPodInstance);

        Assert.assertEquals(String.format("%s-%s.%s", POD_NAME, TestConstants.TASK_NAME, serviceNameWithoutSlashes),
                certificateNamesGenerator.getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());

        List<String> names = Arrays.stream(certificateNamesGenerator.getSANs().getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(taskDnsName(TestConstants.TASK_NAME, serviceNameWithoutSlashes)));
        Assert.assertFalse(names.contains(taskDnsName("*", serviceNameWithoutSlashes)));
        Assert.assertFalse(names.contains(taskVipName("*", serviceNameWithoutSlashes)));
        // echo -n "some-pod-test-task-name.servicenamewithslashes.autoip.dcos.thisdcos.directory" | sha1sum
        Assert.assertEquals("c535f13128f2f15d1765f151114908b41c1eed65", certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testDiscoveryNameAddedAsSan() {
        Mockito.when(mockTaskSpec.getDiscovery()).thenReturn(Optional.of(mockDiscoverySpec));
        Mockito.when(mockDiscoverySpec.getPrefix()).thenReturn(Optional.of("custom-name"));
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(1, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(1, names.size());
        Assert.assertTrue(names.contains(String.format("custom-name-0.%s.%s", TestConstants.SERVICE_NAME, Constants.DNS_TLD)));
        // echo -n "custom-name-0.service-name.autoip.dcos.thisdcos.directory" | sha1sum
        Assert.assertEquals("6ce3490a694a0917beec2bd5f7ac978be7a59ef0", certificateNamesGenerator.getSANsHash());
    }

    @Test
    public void testVipsAddedAsSans() {
        Mockito.when(mockResourceSet.getResources()).thenReturn(Collections.singletonList(mockVIPSpec));
        Mockito.when(mockVIPSpec.getVipName()).thenReturn("test-vip");
        Mockito.when(mockVIPSpec.getPort()).thenReturn(8000L);
        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(TestConstants.SERVICE_NAME, mockTaskSpec, mockPodInstance);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(2, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());
        Assert.assertEquals(2, names.size());
        Assert.assertTrue(names.contains(taskDnsName(TestConstants.TASK_NAME, TestConstants.SERVICE_NAME)));
        Assert.assertTrue(names.contains(taskVipName("test-vip", TestConstants.SERVICE_NAME)));
        // echo -n "some-pod-test-task-name.service-name.autoip.dcos.thisdcos.directory;test-vip.service-name.l4lb.thisdcos.directory" | sha1sum
        Assert.assertEquals("99f8ec48101c439ce41eb62662056dc0ff5d227a", certificateNamesGenerator.getSANsHash());
    }

    private static String taskDnsName(String taskName, String serviceName) {
         return String.format("%s-%s.%s.%s", POD_NAME, taskName, serviceName, Constants.DNS_TLD);
    }

    private static String taskVipName(String vipName, String serviceName) {
        return String.format("%s.%s.%s", vipName, serviceName, Constants.VIP_HOST_TLD);
    }
}
