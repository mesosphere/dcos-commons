/*
Package client implements a set of common functionality that are used by
DC/OS SDK commands to talk to schedulers built using the SDK or other DC/OS
components (e.g. Cosmos). Cluster URL and other global values are pulled
from the dcos-commons/cli/config package and populated by the values configured
using the DC/OS CLI itself.
*/
package client

import (
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"
	"os"
	"path"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// HTTPServiceGet triggers a HTTP GET request to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceGet(urlPath string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPRequest("GET", urlPath)))
}

// HTTPServiceGetQuery triggers a HTTP GET request with query parameters to:
// <config.DcosURL>/service/<config.ServiceName><urlPath>?<urlQuery>
func HTTPServiceGetQuery(urlPath, urlQuery string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPQueryRequest("GET", urlPath, urlQuery)))
}

// HTTPServiceGetData triggers a HTTP GET request with a payload of contentType to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceGetData(urlPath string, payload []byte, contentType string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPDataRequest("GET", urlPath, payload, contentType)))
}

// HTTPServiceGetJSON triggers a HTTP GET request containing jsonPayload to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceGetJSON(urlPath string, jsonPayload []byte) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPJSONRequest("GET", urlPath, jsonPayload)))
}

// HTTPServiceDelete triggers a HTTP DELETE request to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceDelete(urlPath string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPRequest("DELETE", urlPath)))
}

// HTTPServiceDeleteQuery triggers a HTTP DELETE request with query parameters to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>?<urlQuery>
func HTTPServiceDeleteQuery(urlPath, urlQuery string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPQueryRequest("DELETE", urlPath, urlQuery)))
}

// HTTPServiceDeleteData triggers a HTTP DELETE request with a payload of contentType to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceDeleteData(urlPath string, payload []byte, contentType string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPDataRequest("DELETE", urlPath, payload, contentType)))
}

// HTTPServiceDeleteJSON triggers a HTTP DELETE request containing jsonPayload to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceDeleteJSON(urlPath string, jsonPayload []byte) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPJSONRequest("DELETE", urlPath, jsonPayload)))
}

// HTTPServicePost triggers a HTTP POST request to: <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePost(urlPath string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPRequest("POST", urlPath)))
}

// HTTPServicePostQuery triggers a HTTP POST request with query parameters to:
// <config.DcosURL>/service/<config.ServiceName><urlPath>?<urlQuery>
func HTTPServicePostQuery(urlPath, urlQuery string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPQueryRequest("POST", urlPath, urlQuery)))
}

// HTTPServicePostData triggers a HTTP POST request with a payload of contentType to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePostData(urlPath string, payload []byte, contentType string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPDataRequest("POST", urlPath, payload, contentType)))
}

// HTTPServicePostJSON triggers a HTTP POST request containing jsonPayload to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePostJSON(urlPath string, jsonPayload []byte) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPJSONRequest("POST", urlPath, jsonPayload)))
}

// HTTPServicePut triggers a HTTP PUT request to: <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePut(urlPath string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPRequest("PUT", urlPath)))
}

// HTTPServicePutQuery triggers a HTTP PUT request with query parameters to:
// <config.DcosURL>/service/<config.ServiceName><urlPath>?<urlQuery>
func HTTPServicePutQuery(urlPath, urlQuery string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPQueryRequest("PUT", urlPath, urlQuery)))
}

// HTTPServicePutData triggers a HTTP PUT request with a payload of contentType to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePutData(urlPath string, payload []byte, contentType string) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPDataRequest("PUT", urlPath, payload, contentType)))
}

// HTTPServicePutJSON triggers a HTTP PUT request containing jsonPayload to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePutJSON(urlPath string, jsonPayload []byte) ([]byte, error) {
	return CheckHTTPResponse(HTTPQuery(CreateServiceHTTPJSONRequest("PUT", urlPath, jsonPayload)))
}

// HTTPQuery does a HTTP query, returning the response if it's successful, or an error otherwise
func HTTPQuery(request *http.Request) (*http.Response, error) {
	client := &http.Client{
		Transport: &http.Transport{
			// Support for 'HTTP[S]_PROXY'/'NO_PROXY' envvars
			Proxy: http.ProxyFromEnvironment,
			// Support main CLI's 'core.ssl_verify' setting
			TLSClientConfig: buildTLSConfig(),
		},
	}
	return client.Do(request)
}

// CreateServiceHTTPJSONRequest creates a service HTTP JSON request
func CreateServiceHTTPJSONRequest(method, urlPath string, jsonPayload []byte) *http.Request {
	return CreateServiceHTTPDataRequest(method, urlPath, jsonPayload, "application/json")
}

// CreateServiceHTTPDataRequest creates a service HTTP data request
func CreateServiceHTTPDataRequest(method, urlPath string, jsonPayload []byte, contentType string) *http.Request {
	return CreateHTTPRawRequest(method, CreateServiceURL(urlPath, ""), jsonPayload, "", contentType)
}

// CreateServiceHTTPQueryRequest creates a service HTTP query request
func CreateServiceHTTPQueryRequest(method, urlPath, urlQuery string) *http.Request {
	return CreateHTTPRawRequest(method, CreateServiceURL(urlPath, urlQuery), nil, "", "")
}

// CreateServiceHTTPRequest creates a service HTTP request
func CreateServiceHTTPRequest(method, urlPath string) *http.Request {
	return CreateHTTPRawRequest(method, CreateServiceURL(urlPath, ""), nil, "", "")
}

// CreateHTTPRawRequest creates an HTTP request
func CreateHTTPRawRequest(method string, url *url.URL, payload []byte, accept, contentType string) *http.Request {
	return CreateHTTPURLRequest(method, url, payload, accept, contentType)
}

// GetDCOSURL gets the DC/OS cluster URL in the form of "http://cluster-host.com"
func GetDCOSURL() string {
	// get data from CLI or from envar, and trim e.g. "/" or "/#/" from copy-pasted Dashboard URLs:
	return strings.TrimRight(RequiredCLIConfigValue(
		"core.dcos_url",
		"DC/OS Cluster URL",
		"Run 'dcos config set core.dcos_url http://your-cluster.com' to configure"),
		"#/")
}

// CreateServiceURL creates a service URL of the form http://clusterurl.com/service/<servicename>/<urlPath>[?urlQuery]
func CreateServiceURL(urlPath, urlQuery string) *url.URL {
	joinedURLPath := path.Join("service", config.ServiceName, urlPath)
	return CreateURL(GetDCOSURL(), joinedURLPath, urlQuery)
}

// CreateURL creates a URL of the form <baseURL>/<urlPath>[?urlQuery]
func CreateURL(baseURL, urlPath, urlQuery string) *url.URL {
	parsedURL, err := url.Parse(baseURL)
	if err != nil {
		PrintMessage("Unable to parse DC/OS Cluster URL '%s': %s", baseURL, err)
		os.Exit(1)
	}
	parsedURL.Path = urlPath
	parsedURL.RawQuery = urlQuery
	return parsedURL
}

// CreateHTTPURLRequest creates a HTTP url request which includes cluster auth headers as needed.
func CreateHTTPURLRequest(method string, url *url.URL, payload []byte, accept, contentType string) *http.Request {
	request, err := http.NewRequest(method, url.String(), bytes.NewReader(payload))
	if err != nil {
		PrintMessage("Failed to create HTTP %s request for %s: %s", method, url, err)
		os.Exit(1)
	}
	// This value is optional: clusters can be configured to not require any auth
	authToken := OptionalCLIConfigValue("core.dcos_acs_token")
	if len(authToken) != 0 {
		request.Header.Set("Authorization", fmt.Sprintf("token=%s", authToken))
	}
	if len(accept) != 0 {
		request.Header.Set("Accept", accept)
	}
	if len(contentType) != 0 {
		request.Header.Set("Content-Type", contentType)
	}
	if len(payload) > 0 {
		PrintVerbose("HTTP Query (%d byte payload): %s %s\n%s", len(payload), method, url, string(payload))
	} else {
		PrintVerbose("HTTP Query: %s %s", method, url)
	}
	return request
}

// buildTLSConfig returns a new tls.Config object which honors the CLI 'ssl_verify' setting.
func buildTLSConfig() *tls.Config {
	rawTLSSetting := OptionalCLIConfigValue("core.ssl_verify")
	if strings.EqualFold(rawTLSSetting, "false") {
		// 'false' (case insensitive): disable cert validation
		return &tls.Config{InsecureSkipVerify: true}
	} else if strings.EqualFold(rawTLSSetting, "true") {
		// 'true' (case insensitive): require validation against default CAs
		return &tls.Config{InsecureSkipVerify: false}
	} else if len(rawTLSSetting) != 0 {
		// '<other string>': path to local/custom cert file
		cert, err := ioutil.ReadFile(rawTLSSetting)
		if err != nil {
			PrintMessage("Unable to read from CA certificate file %s: %s", rawTLSSetting, err)
			os.Exit(1)
		}
		certPool := x509.NewCertPool()
		certPool.AppendCertsFromPEM(cert)
		return &tls.Config{
			InsecureSkipVerify: false,
			RootCAs:            certPool,
		}
	} else { // len(rawTLSSetting) == 0
		// This shouldn't happen: 'dcos auth login' requires a non-empty setting.
		// Play it safe and leave cert verification enabled by default (equivalent to 'true' case)
		return &tls.Config{InsecureSkipVerify: false}
	}
}
