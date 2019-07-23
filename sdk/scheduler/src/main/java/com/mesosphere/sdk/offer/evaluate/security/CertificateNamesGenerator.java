package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.TaskSpec;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link CertificateNamesGenerator} creates relevant TLS certificate names for given pod instance.
 */
public class CertificateNamesGenerator {

  private static final MessageDigest SHA1_HASHER;

  // Based on the RFC5280 the CN cannot be longer than 64 characters
  // ub-common-name INTEGER ::= 64
  private static final int CN_MAX_LENGTH = 64;

  static {
    try {
      SHA1_HASHER = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private final SchedulerConfig schedulerConfig;

  private final String serviceName;

  private final String taskInstanceName;

  private final String autoIpHostname;

  private final Collection<NamedVIPSpec> vipSpecs;

  public CertificateNamesGenerator(String serviceName,
                                   TaskSpec taskSpec,
                                   PodInstance podInstance,
                                   SchedulerConfig schedulerConfig)
  {
    this.schedulerConfig = schedulerConfig;
    this.serviceName = serviceName;
    this.taskInstanceName = CommonIdUtils.getTaskInstanceName(podInstance, taskSpec);
    // Task can specify its own service discovery name
    if (taskSpec.getDiscovery().isPresent() &&
        taskSpec.getDiscovery().get().getPrefix().isPresent())
    {
      this.autoIpHostname = EndpointUtils.toAutoIpHostname(
          serviceName,
          String.format(
              "%s-%d",
              taskSpec.getDiscovery().get().getPrefix().get(),
              podInstance.getIndex()
          ),
          schedulerConfig);
    } else {
      this.autoIpHostname =
          EndpointUtils.toAutoIpHostname(serviceName, this.taskInstanceName, schedulerConfig);
    }
    this.vipSpecs = taskSpec.getResourceSet().getResources().stream()
        .filter(resourceSpec -> resourceSpec instanceof NamedVIPSpec)
        .map(resourceSpec -> (NamedVIPSpec) resourceSpec)
        .collect(Collectors.toList());
  }

  /**
   * Returns a Subject for service certificate.
   */
  public X500Name getSubject() {
    // Create subject CN as pod-name-0-task-name.service-name
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
   */
  public GeneralNames getSANs() {
    List<GeneralName> generalNames = new ArrayList<>();
    generalNames.add(new GeneralName(GeneralName.dNSName, autoIpHostname));

    // Process VIP names, if any
    vipSpecs.stream()
        .map(vipSpec -> new GeneralName(
            GeneralName.dNSName,
            EndpointUtils.toVipHostname(
                serviceName,
                schedulerConfig,
                new EndpointUtils.VipInfo(vipSpec.getVipName(), (int) vipSpec.getPort()))))
        .forEach(generalNames::add);

    return new GeneralNames(generalNames.toArray(new GeneralName[0]));
  }

  /**
   * Creates SHA1 string representation of {@link #getSANs()}.
   */
  public String getSANsHash() {
    String allSans = Arrays.stream(getSANs().getNames())
        .map(name -> name.getName().toString())
        .collect(Collectors.joining(";"));
    byte[] digest = SHA1_HASHER.digest(allSans.getBytes(StandardCharsets.UTF_8));
    return new String(Hex.encode(digest), StandardCharsets.UTF_8);
  }
}
