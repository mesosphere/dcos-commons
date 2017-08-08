package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

public class CertificateNamesGeneratorTest {

    @Test
    public void getSubject() throws Exception {
        CertificateNamesGenerator certificateNamesGenerator = new CertificateNamesGenerator(
                TestConstants.SERVICE_NAME, TestConstants.TASK_NAME, Optional.empty(), Collections.emptyList());

        X500Name subject = certificateNamesGenerator.getSubject();

        String expectedCNName = String.format("%s.%s", TestConstants.TASK_NAME, TestConstants.SERVICE_NAME);

        RDN[] cnRDNs = subject.getRDNs(BCStyle.CN);
        Assert.assertEquals(cnRDNs.length, 1);
        Assert.assertEquals(expectedCNName, cnRDNs[0].getFirst().getValue().toString());
    }

    @Test
    public void getSubjectWithLongCN() throws Exception {
        String taskName = UUID.randomUUID().toString();
        String serviceName = UUID.randomUUID().toString();
        CertificateNamesGenerator certificateNamesGenerator = new CertificateNamesGenerator(
                serviceName, taskName, Optional.empty(), Collections.emptyList());

        X500Name subject = certificateNamesGenerator.getSubject();
        RDN[] cnRDNs = subject.getRDNs(BCStyle.CN);


        Assert.assertEquals(cnRDNs.length, 1);
        Assert.assertEquals(64, cnRDNs[0].getFirst().getValue().toString().length());
    }

    @Test
    public void getSANs() throws Exception {
        CertificateNamesGenerator certificateNamesGenerator = new CertificateNamesGenerator(
                TestConstants.SERVICE_NAME, TestConstants.TASK_NAME, Optional.empty(), Collections.emptyList());

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(1, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());

        String dnsNameWithTaskName = taskDnsName(
                TestConstants.TASK_NAME,
                TestConstants.SERVICE_NAME);

        String wildcardDnsName = taskDnsName("*", TestConstants.SERVICE_NAME);

        String wildcardVipName = taskVipName("*", TestConstants.SERVICE_NAME);

        Assert.assertTrue(names.contains(dnsNameWithTaskName));
        Assert.assertFalse(names.contains(wildcardDnsName));
        Assert.assertFalse(names.contains(wildcardVipName));
    }

    @Test
    public void testSlashesInServiceName() throws Exception {
        String serviceNameWithSlashes = "service/name/with/slashes";
        String serviceNameWithoutSlashes = "servicenamewithslashes";

        String expectedCNName = String.format("%s.%s", TestConstants.TASK_NAME, serviceNameWithoutSlashes);

        CertificateNamesGenerator certificateNamesGenerator = new CertificateNamesGenerator(
                serviceNameWithSlashes, TestConstants.TASK_NAME, Optional.empty(), Collections.emptyList());

        String cnName = certificateNamesGenerator
                .getSubject()
                .getRDNs(BCStyle.CN)[0]
                .getFirst()
                .getValue()
                .toString();
        Assert.assertEquals(expectedCNName, cnName);

        List<String> names = Arrays.stream(certificateNamesGenerator.getSANs().getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());

        String dnsNameWithTaskName = taskDnsName(
                TestConstants.TASK_NAME,
                serviceNameWithoutSlashes);

        String wildcardDnsName = taskDnsName("*", serviceNameWithoutSlashes);

        String wildcardVipName = taskVipName("*", serviceNameWithoutSlashes);

        Assert.assertTrue(names.contains(dnsNameWithTaskName));
        Assert.assertFalse(names.contains(wildcardDnsName));
        Assert.assertFalse(names.contains(wildcardVipName));
    }

    @Test
    public void testDiscoveryNameAddedAsSan() {
        CertificateNamesGenerator certificateNamesGenerator = new CertificateNamesGenerator(
                TestConstants.SERVICE_NAME, TestConstants.TASK_NAME, Optional.of("custom-name-0"), Collections.emptyList());

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(1, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());

        String dnsNameWithDiscoveryName = taskDnsName(
                "custom-name-0",
                TestConstants.SERVICE_NAME);
        Assert.assertTrue(names.contains(dnsNameWithDiscoveryName));
    }

    @Test
    public void testVipsAddedAsSans() {
        List<NamedVIPSpec> namedVIPSpecs = Arrays.asList(
                getNamedVIPSpec(8000, Collections.emptyList()));

        CertificateNamesGenerator certificateNamesGenerator = new CertificateNamesGenerator(
                TestConstants.SERVICE_NAME, TestConstants.TASK_NAME, Optional.empty(), namedVIPSpecs);

        GeneralNames sans = certificateNamesGenerator.getSANs();
        Assert.assertEquals(2, sans.getNames().length);

        List<String> names = Arrays.stream(sans.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.toList());

        String dnsNameWithDiscoveryName = taskVipName(
                "test-vip",
                TestConstants.SERVICE_NAME);
        Assert.assertTrue(names.contains(dnsNameWithDiscoveryName));
    }

    private String taskDnsName(String taskName, String serviceName) {
         return String.format(
            "%s.%s.%s",
            taskName,
            serviceName,
            Constants.DNS_TLD);
    }

    private String taskVipName(String taskName, String serviceName) {
        return String.format(
            "%s.%s.%s",
            taskName,
            serviceName,
            Constants.VIP_HOST_TLD);
    }

    private static NamedVIPSpec getNamedVIPSpec(int taskPort, Collection<String> networkNames) {
        Protos.Value.Builder valueBuilder = Protos.Value.newBuilder()
                .setType(Protos.Value.Type.RANGES);
        valueBuilder.getRangesBuilder().addRangeBuilder()
                .setBegin(taskPort)
                .setEnd(taskPort);

        return new NamedVIPSpec(
                valueBuilder.build(),
                TestConstants.ROLE,
                Constants.ANY_ROLE,
                TestConstants.PRINCIPAL,
                TestConstants.PORT_ENV_NAME + "_VIP_" + taskPort,
                TestConstants.VIP_NAME + "-" + taskPort,
                "sctp",
                Protos.DiscoveryInfo.Visibility.EXTERNAL,
                "test-vip",
                80,
                networkNames);
    }

}
