package com.mesosphere.sdk.dcos.clients;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.DcosVersion;

import com.google.common.annotations.VisibleForTesting;
import org.apache.http.client.fluent.Request;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Optional;

/**
 * Fetches the DC/OS cluster version from the cluster itself.
 */
public class DcosVersionClient {
  private static final String DCOS_VERSION_PATH = "/dcos-metadata/dcos-version.json";

  @VisibleForTesting
  static final Request DCOS_VERSION_REQUEST = Request
      .Get(DcosConstants.MESOS_LEADER_URI + DCOS_VERSION_PATH);

  private final DcosHttpExecutor httpExecutor;

  private Optional<DcosVersion> dcosVersion = Optional.empty();

  public DcosVersionClient(DcosHttpExecutor httpExecutor) {
    this.httpExecutor = httpExecutor;
  }

  public DcosVersion getDcosVersion() throws IOException {
    if (!dcosVersion.isPresent()) {
      dcosVersion = Optional.of(new DcosVersion(new JSONObject(
          httpExecutor
              .execute(DCOS_VERSION_REQUEST)
              .returnContent()
              .toString()
      )));
    }
    return dcosVersion.get();
  }
}
