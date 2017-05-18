package client

import (
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"path"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
)

func HTTPServiceGet(urlPath string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPRequest("GET", urlPath)))
}

func HTTPServiceGetQuery(urlPath, urlQuery string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPQueryRequest("GET", urlPath, urlQuery)))
}

func HTTPServiceGetData(urlPath, payload, contentType string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPDataRequest("GET", urlPath, payload, contentType)))
}

func HTTPServiceGetJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPJSONRequest("GET", urlPath, jsonPayload)))
}

func HTTPServiceDelete(urlPath string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPRequest("DELETE", urlPath)))
}

func HTTPServiceDeleteQuery(urlPath, urlQuery string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPQueryRequest("DELETE", urlPath, urlQuery)))
}

func HTTPServiceDeleteData(urlPath, payload, contentType string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPDataRequest("DELETE", urlPath, payload, contentType)))
}

func HTTPServiceDeleteJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPJSONRequest("DELETE", urlPath, jsonPayload)))
}

func HTTPServicePost(urlPath string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPRequest("POST", urlPath)))
}

func HTTPServicePostQuery(urlPath, urlQuery string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPQueryRequest("POST", urlPath, urlQuery)))
}

func HTTPServicePostData(urlPath, payload, contentType string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPDataRequest("POST", urlPath, payload, contentType)))
}

func HTTPServicePostJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPJSONRequest("POST", urlPath, jsonPayload)))
}

func HTTPCosmosPostJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createCosmosHTTPJSONRequest("POST", urlPath, jsonPayload)))
}

func HTTPServicePut(urlPath string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPRequest("PUT", urlPath)))
}

func HTTPServicePutQuery(urlPath, urlQuery string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPQueryRequest("PUT", urlPath, urlQuery)))
}

func HTTPServicePutData(urlPath, payload, contentType string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPDataRequest("PUT", urlPath, payload, contentType)))
}

func HTTPServicePutJSON(urlPath, jsonPayload string) *http.Response {
	return checkHTTPResponse(httpQuery(createServiceHTTPJSONRequest("PUT", urlPath, jsonPayload)))
}

func httpQuery(request *http.Request) *http.Response {
	if config.TlsForceInsecure { // user override via '--force-insecure'
		config.TlsCliSetting = config.TlsUnverified
	}
	if config.TlsCliSetting == config.TlsUnknown {
		// get CA settings from CLI
		cliVerifySetting := OptionalCLIConfigValue("core.ssl_verify")
		if strings.EqualFold(cliVerifySetting, "false") {
			// 'false': disable cert validation
			config.TlsCliSetting = config.TlsUnverified
		} else if strings.EqualFold(cliVerifySetting, "true") {
			// 'true': require validation against default CAs
			config.TlsCliSetting = config.TlsVerified
		} else if len(cliVerifySetting) != 0 {
			// '<other string>': path to local/custom cert file
			if len(config.TlsCACertPath) == 0 {
				config.TlsCACertPath = cliVerifySetting
			}
			config.TlsCliSetting = config.TlsSpecificCert
		} else {
			// this shouldn't happen: 'auth login' requires a non-empty setting.
			// play it safe and leave cert verification enabled by default.
			config.TlsCliSetting = config.TlsVerified
		}
	}

	// allow unverified certs if user invoked --force-insecure, or if it's configured that way in CLI:
	tlsConfig := &tls.Config{InsecureSkipVerify: (config.TlsCliSetting == config.TlsUnverified)}

	// import custom cert if user manually set the flag, or if it's configured in CLI:
	if len(config.TlsCACertPath) != 0 {
		// include custom CA cert as verified
		cert, err := ioutil.ReadFile(config.TlsCACertPath)
		if err != nil {
			log.Fatalf("Unable to read from CA certificate file %s: %s", config.TlsCACertPath, err)
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
			log.Printf("HTTP %s Query for %s failed: %s", request.Method, request.URL, err)
			log.Printf("- Is someone intercepting the connection to steal your credentials?")
			log.Printf("- Is the cluster CA certificate configured correctly? Check 'dcos config show core.ssl_verify'.")
			log.Fatalf("- To ignore the unvalidated certificate and force your command (INSECURE), use --force-insecure")
		default:
			log.Printf("HTTP %s Query for %s failed: %s", request.Method, request.URL, err)
			log.Printf("- Is 'core.dcos_url' set correctly? Check 'dcos config show core.dcos_url'.")
			log.Fatalf("- Is 'core.dcos_acs_token' set correctly? Run 'dcos auth login' to log in.")
		}
	}
	if config.Verbose {
		log.Printf("Response: %s (%d bytes)", response.Status, response.ContentLength)
	}
	return response
}

func checkHTTPResponse(response *http.Response) *http.Response {
	switch {
	case response.StatusCode == 401:
		log.Printf("Got 401 Unauthorized response from %s", response.Request.URL)
		log.Fatalf("- Bad auth token? Run 'dcos auth login' to log in.")
	case response.StatusCode == 500:
		log.Printf("HTTP %s Query for %s failed: %s",
			response.Request.Method, response.Request.URL, response.Status)
		log.Printf("- Did you provide the correct service name? Currently using '%s', specify a different name with '--name=<name>'.", config.ServiceName)
		log.Fatalf("- Was the service recently installed? It may still be initializing, wait a bit and try again.")
	case response.StatusCode < 200 || response.StatusCode >= 300:
		log.Fatalf("HTTP %s Query for %s failed: %s",
			response.Request.Method, response.Request.URL, response.Status)
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

func createCosmosHTTPJSONRequest(method, urlPath, jsonPayload string) *http.Request {
	// NOTE: this explicitly only allows use of /service/ endpoints within Cosmos. See DCOS-15772
	// for the "correct" solution to allow cleaner use of /package/ endpoints.
	endpoint := strings.Replace(urlPath, "/", ".", -1)
	acceptHeader := fmt.Sprintf("application/vnd.dcos.service.%s-response+json;charset=utf-8;version=v1", endpoint)
	contentTypeHeader := fmt.Sprintf("application/vnd.dcos.service.%s-request+json;charset=utf-8;version=v1", endpoint)
	return createHTTPRawRequest(method, createCosmosURL(urlPath), jsonPayload, acceptHeader, contentTypeHeader)
}

func createHTTPRawRequest(method string, url *url.URL, payload, accept, contentType string) *http.Request {
	return createHTTPURLRequest(method, url, payload, accept, contentType)
}

func getDCOSURL() {
	// get data from CLI, if overrides were not provided by user:
	if len(config.DcosUrl) == 0 {
		config.DcosUrl = RequiredCLIConfigValue(
			"core.dcos_url",
			"DC/OS Cluster URL",
			"Run 'dcos config set core.dcos_url http://your-cluster.com' to configure.")
	}
	// Trim eg "/#/" from copy-pasted Dashboard URL:
	config.DcosUrl = strings.TrimRight(config.DcosUrl, "#/")
}

func createServiceURL(urlPath, urlQuery string) *url.URL {
	getDCOSURL()
	joinedURLPath := path.Join("service", config.ServiceName, urlPath)
	return createURL(config.DcosUrl, joinedURLPath, urlQuery)
}

func createCosmosURL(urlPath string) *url.URL {
	// Try to fetch the Cosmos URL from the system configuration
	if len(config.CosmosUrl) == 0 {
		config.CosmosUrl = OptionalCLIConfigValue("package.cosmos_url")
	}

	// Use Cosmos URL if we have it specified
	if len(config.CosmosUrl) > 0 {
		joinedURLPath := path.Join("service", urlPath) // e.g. https://<cosmos_url>/service/describe
		return createURL(config.CosmosUrl, joinedURLPath, "")
	}
	getDCOSURL()
	joinedURLPath := path.Join("cosmos", "service", urlPath) // e.g. https://<dcos_url>/cosmos/service/describe
	return createURL(config.DcosUrl, joinedURLPath, "")
}

func createURL(baseURL, urlPath, urlQuery string) *url.URL {
	parsedURL, err := url.Parse(baseURL)
	if err != nil {
		log.Fatalf("Unable to parse DC/OS Cluster URL '%s': %s", config.DcosUrl, err)
	}
	parsedURL.Path = urlPath
	parsedURL.RawQuery = urlQuery
	return parsedURL
}

func createHTTPURLRequest(method string, url *url.URL, payload, accept, contentType string) *http.Request {
	if config.Verbose {
		log.Printf("HTTP Query: %s %s", method, url)
		if len(payload) != 0 {
			log.Printf("  Payload: %s", payload)
		}
	}
	request, err := http.NewRequest(method, url.String(), bytes.NewReader([]byte(payload)))
	if err != nil {
		log.Fatalf("Failed to create HTTP %s request for %s: %s", method, url, err)
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
