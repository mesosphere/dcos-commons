package com.mesosphere.sdk.hdfs.scheduler;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class helps to generate user auth mappings for the HDFS setup with kerberos.
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class HDFSUserAuthMapperBuilder {
  private static final String authToLocalDefaultTemplate = "RULE:[2:$1/$2@$0](%s/%s.%s@%s)s/.*/%s/";

  private final ArrayList<String> authMappings = new ArrayList<>();

  private final String frameworkHost;

  private final HDFSAuthEnvContainer envContainer;

  HDFSUserAuthMapperBuilder(Map<String, String> env, String frameworkHost) {
    this.frameworkHost = frameworkHost;
    this.envContainer = new HDFSAuthEnvContainer(env);
  }

  HDFSUserAuthMapperBuilder addUserAuthMappingFromEnv() {
    authMappings.add(envContainer.getEnvAuthMapping());
    return this;
  }

  HDFSUserAuthMapperBuilder addDefaultUserAuthMapping(
      String hostPrefix,
      String hostPostfix,
      int nodeCount)
  {
    for (int i = 0; i < nodeCount; i++) {
      String taskName = String.format("%s-%s-%s", hostPrefix, i, hostPostfix);
      authMappings.add(
          String.format(
              authToLocalDefaultTemplate,
              envContainer.getPrimary(),
              taskName,
              frameworkHost,
              envContainer.getRealm(),
              envContainer.getFrameworkUser()
          )
      );
    }
    return this;
  }

  String build() {
    return authMappings
        .stream()
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining("\n"));
  }
}
