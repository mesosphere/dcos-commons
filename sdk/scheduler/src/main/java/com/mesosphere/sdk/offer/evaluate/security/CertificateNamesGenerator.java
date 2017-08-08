package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;

import java.util.*;

/**
* A {@link CertificateNamesGenerator} creates relevant names for given service pod.
 */
public class CertificateNamesGenerator {

    private final String serviceName;
    private final String taskInstanceName;
    private final Optional<String> discoveryName;
    private final Collection<NamedVIPSpec> vipSpecs;

    // Based on the RFC5280 the CN cannot be longer than 64 characters
    // ub-common-name INTEGER ::= 64
    private static final int CN_MAX_LENGTH = 64;

    public CertificateNamesGenerator(
            String serviceName,
            String taskInstanceName,
            Optional<String> discoveryName,
            Collection<NamedVIPSpec> vipSpecs) {
        this.serviceName = serviceName;
        this.taskInstanceName = taskInstanceName;
        this.discoveryName = discoveryName;
        this.vipSpecs = vipSpecs;
    }

    /**
     * Returns a Subject for service certificate.
     * @return
     */
    public X500Name getSubject() {
        // Create subject CN as task-name.service-name
        String cn = String.format("%s.%s",
                EndpointUtils.removeSlashes(EndpointUtils.replaceDotsWithDashes(taskInstanceName)),
                EndpointUtils.removeSlashes(EndpointUtils.replaceDotsWithDashes(serviceName)));

        if (cn.length() > CN_MAX_LENGTH) {
            cn = cn.substring(cn.length() - CN_MAX_LENGTH);
        }

        return new X500NameBuilder()
                .addRDN(BCStyle.CN, cn)
                .addRDN(BCStyle.O, "Mesosphere, Inc")
                .addRDN(BCStyle.L, "San Francisco")
                .addRDN(BCStyle.ST, "CA")
                .addRDN(BCStyle.C, "US")
                .build();
    }

    /**
     * Returns additional Subject Alternative Names for service certificates.
     * @return
     */
    public GeneralNames getSANs() {
        List<GeneralName> generalNames = new ArrayList<>(Arrays.asList(
                new GeneralName(GeneralName.dNSName, getAutoIpHostname())
        ));

        // Process VIP names
        vipSpecs
                .stream()
                .map(vipSpec -> new EndpointUtils.VipInfo(vipSpec.getVipName(), (int) vipSpec.getPort()))
                .map(vipInfo -> EndpointUtils.toVipHostname(serviceName, vipInfo))
                .map(vipHostname -> new GeneralName(GeneralName.dNSName, vipHostname))
                .forEach(vipGeneralName -> generalNames.add(vipGeneralName));


        return new GeneralNames(
                generalNames.toArray(
                        new GeneralName[generalNames.size()])
        );
    }

    private String getAutoIpHostname() {
        if (this.discoveryName.isPresent()) {
            return EndpointUtils.toAutoIpHostname(serviceName, this.discoveryName.get());
        } else {
            return EndpointUtils.toAutoIpHostname(serviceName, taskInstanceName);
        }
    }

}
