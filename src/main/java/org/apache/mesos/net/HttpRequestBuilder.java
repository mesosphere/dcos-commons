package org.apache.mesos.net;

import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Used to build a http request.  The minimum required is host (foo) and port (867) resulting in
 * http://foo:867/.
 * In addition this builder will help with paths and query strings.  It handles the basic cases.
 */
public class HttpRequestBuilder {

  // todo:  need to look at leveraging URI.create()
  private static final String protocol = "http://";

  String host;
  int port;
  String path;
  String query;

  StringBuilder url = new StringBuilder(protocol);

  public HttpRequestBuilder() {
  }

  public HttpRequestBuilder(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setPath(String path) {
    if (StringUtils.isBlank(path)) {
      return;
    }
    if (path.startsWith("/")) {
      path = path.substring(1);
    }
    this.path = path;
  }

  public void setQuery(String query) {
    if (StringUtils.isBlank(query)) {
      return;
    }
    if (query.startsWith("?")) {
      query = query.substring(1);
    }
    this.query = query;
  }

  public String getHttpRequest() throws MalformedURLException {
    validHost(host);
    validPort(port);
    url.append(host).append(":").append(port);
    if (StringUtils.isNotBlank(path)) {
      url.append("/").append(path);
    }
    if (StringUtils.isNotBlank(query)) {
      url.append("?").append(query);
    }
    return url.toString();
  }

  public URL getURL() throws MalformedURLException {
    return new URL(getHttpRequest());
  }

  private void validPort(int port) throws MalformedURLException {
    if (port <= 0) {
      throw new MalformedURLException("port is expected to be greater than 0.");
    }
  }

  private void validHost(String host) throws MalformedURLException {
    if (StringUtils.isBlank(host)) {
      throw new MalformedURLException("host or IP is required.");
    }
  }
}
