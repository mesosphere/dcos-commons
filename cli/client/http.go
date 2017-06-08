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
	"path"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// HTTPServiceGet triggers a HTTP GET request to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceGet(urlPath string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPRequest("GET", urlPath)))
}

// HTTPServiceGetQuery triggers a HTTP GET request with query parameters to:
// <config.DcosURL>/service/<config.ServiceName><urlPath>?<urlQuery>
func HTTPServiceGetQuery(urlPath, urlQuery string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPQueryRequest("GET", urlPath, urlQuery)))
}

// HTTPServiceGetData triggers a HTTP GET request with a payload of contentType to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceGetData(urlPath, payload, contentType string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPDataRequest("GET", urlPath, payload, contentType)))
}

// HTTPServiceGetJSON triggers a HTTP GET request containing jsonPayload to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceGetJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPJSONRequest("GET", urlPath, jsonPayload)))
}

// HTTPServiceDelete triggers a HTTP DELETE request to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceDelete(urlPath string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPRequest("DELETE", urlPath)))
}

// HTTPServiceDeleteQuery triggers a HTTP DELETE request with query parameters to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>?<urlQuery>
func HTTPServiceDeleteQuery(urlPath, urlQuery string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPQueryRequest("DELETE", urlPath, urlQuery)))
}

// HTTPServiceDeleteData triggers a HTTP DELETE request with a payload of contentType to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceDeleteData(urlPath, payload, contentType string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPDataRequest("DELETE", urlPath, payload, contentType)))
}

// HTTPServiceDeleteJSON triggers a HTTP DELETE request containing jsonPayload to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServiceDeleteJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPJSONRequest("DELETE", urlPath, jsonPayload)))
}

// HTTPServicePost triggers a HTTP POST request to: <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePost(urlPath string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPRequest("POST", urlPath)))
}

// HTTPServicePostQuery triggers a HTTP POST request with query parameters to:
// <config.DcosURL>/service/<config.ServiceName><urlPath>?<urlQuery>
func HTTPServicePostQuery(urlPath, urlQuery string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPQueryRequest("POST", urlPath, urlQuery)))
}

// HTTPServicePostData triggers a HTTP POST request with a payload of contentType to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePostData(urlPath, payload, contentType string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPDataRequest("POST", urlPath, payload, contentType)))
}

// HTTPServicePostJSON triggers a HTTP POST request containing jsonPayload to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePostJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPJSONRequest("POST", urlPath, jsonPayload)))
}

// HTTPServicePut triggers a HTTP PUT request to: <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePut(urlPath string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPRequest("PUT", urlPath)))
}

// HTTPServicePutQuery triggers a HTTP PUT request with query parameters to:
// <config.DcosURL>/service/<config.ServiceName><urlPath>?<urlQuery>
func HTTPServicePutQuery(urlPath, urlQuery string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPQueryRequest("PUT", urlPath, urlQuery)))
}

// HTTPServicePutData triggers a HTTP PUT request with a payload of contentType to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePutData(urlPath, payload, contentType string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPDataRequest("PUT", urlPath, payload, contentType)))
}

// HTTPServicePutJSON triggers a HTTP PUT request containing jsonPayload to:
// <config.DcosURL>/service/<config.ServiceName>/<urlPath>
func HTTPServicePutJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPJSONRequest("PUT", urlPath, jsonPayload)))
}

func httpQuery(request *http.Request) *http.Response {
	if config.TLSForceInsecure { // user override via '--force-insecure'
		config.TLSCliSetting = config.TLSUnverified
	}
	if config.TLSCliSetting == config.TLSUnknown {
		// get CA settings from CLI
		cliVerifySetting := OptionalCLIConfigValue("core.ssl_verify")
		if strings.EqualFold(cliVerifySetting, "false") {
			// 'false': disable cert validation
			config.TLSCliSetting = config.TLSUnverified
		} else if strings.EqualFold(cliVerifySetting, "true") {
			// 'true': require validation against default CAs
			config.TLSCliSetting = config.TLSVerified
		} else if len(cliVerifySetting) != 0 {
			// '<other string>': path to local/custom cert file
			if len(config.TLSCACertPath) == 0 {
				config.TLSCACertPath = cliVerifySetting
			}
			config.TLSCliSetting = config.TLSSpecificCert
		} else {
			// this shouldn't happen: 'auth login' requires a non-empty setting.
			// play it safe and leave cert verification enabled by default.
			config.TLSCliSetting = config.TLSVerified
		}
	}

	// allow unverified certs if user invoked --force-insecure, or if it's configured that way in CLI:
	tlsConfig := &tls.Config{InsecureSkipVerify: (config.TLSCliSetting == config.TLSUnverified)}

	// import custom cert if user manually set the flag, or if it's configured in CLI:
	if len(config.TLSCACertPath) != 0 {
		// include custom CA cert as verified
		cert, err := ioutil.ReadFile(config.TLSCACertPath)
		if err != nil {
			PrintMessageAndExit("Unable to read from CA certificate file %s: %s", config.TLSCACertPath, err)
		}
		certPool := x509.NewCertPool()
		certPool.AppendCertsFromPEM(cert)
		tlsConfig.RootCAs = certPool
	}

	client := &http.Client{Transport: &http.Transport{TLSClientConfig: tlsConfig}}
	var err interface{}
	response, err := client.Do(request)
	switch err.(type) {
	case *url.Error:
		// extract wrapped error
		err = err.(*url.Error).Err
	}
	if err != nil {
		switch err.(type) {
		case x509.UnknownAuthorityError:
			// custom suggestions for a certificate error:
			PrintMessage("HTTP %s Query for %s failed: %s", request.Method, request.URL, err)
			PrintMessage("- Is the cluster CA certificate configured correctly? Check 'dcos config show core.ssl_verify'.")
			PrintMessageAndExit("- To ignore the unvalidated certificate and force your command (INSECURE), use --force-insecure")
		default:
			PrintMessage("HTTP %s Query for %s failed: %s", request.Method, request.URL, err)
			PrintMessage("- Is 'core.dcos_url' set correctly? Check 'dcos config show core.dcos_url'.")
			PrintMessageAndExit("- Is 'core.dcos_acs_token' set correctly? Run 'dcos auth login' to log in.")
		}
	}
	if config.Verbose {
		PrintMessage("Response: %s (%d bytes)", response.Status, response.ContentLength)
	}
	return response
}

func checkHTTPResponse(response *http.Response) *http.Response {
	switch {
	case response.StatusCode == http.StatusUnauthorized:
		PrintMessage("Got 401 Unauthorized response from %s", response.Request.URL)
		PrintMessageAndExit("- Bad auth token? Run 'dcos auth login' to log in.")
	case response.StatusCode == http.StatusInternalServerError || response.StatusCode == http.StatusBadGateway || response.StatusCode == http.StatusNotFound:
		printServiceNameErrorAndExit(response)
	case response.StatusCode < 200 || response.StatusCode >= 300:
		printResponseErrorAndExit(response)
	}
	return response
}

func createServiceHTTPJSONRequest(method, urlPath, jsonPayload string) *http.Request {
	return createServiceHTTPDataRequest(method, urlPath, jsonPayload, "application/json")
}

func createServiceHTTPDataRequest(method, urlPath, jsonPayload, contentType string) *http.Request {
	return createHTTPRawRequest(method, createServiceURL(urlPath, ""), jsonPayload, "", contentType)
}

func createServiceHTTPQueryRequest(method, urlPath, urlQuery string) *http.Request {
	return createHTTPRawRequest(method, createServiceURL(urlPath, urlQuery), "", "", "")
}

func createServiceHTTPRequest(method, urlPath string) *http.Request {
	return createHTTPRawRequest(method, createServiceURL(urlPath, ""), "", "", "")
}

func createHTTPRawRequest(method string, url *url.URL, payload, accept, contentType string) *http.Request {
	return createHTTPURLRequest(method, url, payload, accept, contentType)
}

func getDCOSURL() {
	// get data from CLI, if overrides were not provided by user:
	if len(config.DcosURL) == 0 {
		config.DcosURL = RequiredCLIConfigValue(
			"core.dcos_url",
			"DC/OS Cluster URL",
			"Run 'dcos config set core.dcos_url http://your-cluster.com' to configure.")
	}
	// Trim eg "/#/" from copy-pasted Dashboard URL:
	config.DcosURL = strings.TrimRight(config.DcosURL, "#/")
}

func createServiceURL(urlPath, urlQuery string) *url.URL {
	getDCOSURL()
	joinedURLPath := path.Join("service", config.ServiceName, urlPath)
	return createURL(config.DcosURL, joinedURLPath, urlQuery)
}

func createURL(baseURL, urlPath, urlQuery string) *url.URL {
	parsedURL, err := url.Parse(baseURL)
	if err != nil {
		PrintMessageAndExit("Unable to parse DC/OS Cluster URL '%s': %s", config.DcosURL, err)
	}
	parsedURL.Path = urlPath
	parsedURL.RawQuery = urlQuery
	return parsedURL
}

func createHTTPURLRequest(method string, url *url.URL, payload, accept, contentType string) *http.Request {
	if config.Verbose {
		PrintMessage("HTTP Query: %s %s", method, url)
		if len(payload) != 0 {
			PrintMessage("  Payload: %s", payload)
		}
	}
	request, err := http.NewRequest(method, url.String(), bytes.NewReader([]byte(payload)))
	if err != nil {
		PrintMessageAndExit("Failed to create HTTP %s request for %s: %s", method, url, err)
	}
	if len(config.DcosAuthToken) == 0 {
		// if the token wasnt manually provided by the user, try to fetch it from the main CLI.
		// this value is optional: clusters can be configured to not require any auth
		config.DcosAuthToken = OptionalCLIConfigValue("core.dcos_acs_token")
	}
	if len(config.DcosAuthToken) != 0 {
		request.Header.Set("Authorization", fmt.Sprintf("token=%s", config.DcosAuthToken))
	}
	if len(accept) != 0 {
		request.Header.Set("Accept", accept)
	}
	if len(contentType) != 0 {
		request.Header.Set("Content-Type", contentType)
	}
	return request
}
