package cli

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
)

var (
	dcosAuthToken      string
	dcosUrl            string
	serviceName        string
	tlsAllowUnverified bool
	tlsCACertPath      string
)

func HTTPGet(urlPath string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPRequest("GET", urlPath)))
}
func HTTPGetQuery(urlPath, urlQuery string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPQueryRequest("GET", urlPath, urlQuery)))
}
func HTTPGetData(urlPath, payload, contentType string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPDataRequest("GET", urlPath, payload, contentType)))
}
func HTTPGetJSON(urlPath, jsonPayload string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPJSONRequest("GET", urlPath, jsonPayload)))
}

func HTTPDelete(urlPath string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPRequest("DELETE", urlPath)))
}
func HTTPDeleteQuery(urlPath, urlQuery string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPQueryRequest("DELETE", urlPath, urlQuery)))
}
func HTTPDeleteData(urlPath, payload, contentType string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPDataRequest("DELETE", urlPath, payload, contentType)))
}
func HTTPDeleteJSON(urlPath, jsonPayload string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPJSONRequest("DELETE", urlPath, jsonPayload)))
}

func HTTPPost(urlPath string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPRequest("POST", urlPath)))
}
func HTTPPostQuery(urlPath, urlQuery string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPQueryRequest("POST", urlPath, urlQuery)))
}
func HTTPPostData(urlPath, payload, contentType string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPDataRequest("POST", urlPath, payload, contentType)))
}
func HTTPPostJSON(urlPath, jsonPayload string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPJSONRequest("POST", urlPath, jsonPayload)))
}

func HTTPPut(urlPath string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPRequest("PUT", urlPath)))
}
func HTTPPutQuery(urlPath, urlQuery string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPQueryRequest("PUT", urlPath, urlQuery)))
}
func HTTPPutData(urlPath, payload, contentType string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPDataRequest("PUT", urlPath, payload, contentType)))
}
func HTTPPutJSON(urlPath, jsonPayload string) *http.Response {
	return CheckHTTPResponse(HTTPQuery(CreateHTTPJSONRequest("PUT", urlPath, jsonPayload)))
}

func HTTPQuery(request *http.Request) *http.Response {
	// get CA settings from CLI (local flags override CLI):
	cliVerifySetting := OptionalCLIConfigValue("core.ssl_verify")
	if strings.EqualFold(cliVerifySetting, "false") {
		// 'false': disable cert validation
		tlsAllowUnverified = true
	} else if strings.EqualFold(cliVerifySetting, "true") {
		// 'true': require validation against default CAs
		// (leave tlsAllowUnverified alone: already defaults to false)
	} else if len(cliVerifySetting) != 0 {
		// '<other string>': path to local/custom cert file
		if len(tlsCACertPath) == 0 {
			tlsCACertPath = cliVerifySetting
		}
	} else {
		// this shouldn't happen: 'auth login' requires a non-empty setting.
		// play it safe and leave cert verification enabled by default.
	}

	// allow unverified certs if user manually set the flag, or if it's configured that way in CLI:
	tlsConfig := &tls.Config{InsecureSkipVerify: tlsAllowUnverified}

	// import custom cert if user manually set the flag, or if it's configured in CLI:
	if len(tlsCACertPath) != 0 {
		// include custom CA cert as verified
		cert, err := ioutil.ReadFile(tlsCACertPath)
		if err != nil {
			log.Fatalf("Unable to read from CA certificate file %s: %s", tlsCACertPath, err)
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
	if response.StatusCode == 401 {
		log.Printf("Got 401 Unauthorized response from %s", request.URL)
		log.Fatalf("- Bad auth token? Run 'dcos auth login' to log in.")
	}
	if Verbose {
		log.Printf("Response: %s (%d bytes)", response.Status, response.ContentLength)
	}
	return response
}

func CheckHTTPResponse(response *http.Response) *http.Response {
	switch {
	case response.StatusCode == 500:
		log.Printf("HTTP %s Query for %s failed: %s",
			response.Request.Method, response.Request.URL, response.Status)
		log.Printf("- Did you provide the correct service name? Currently using '%s', specify a different name with '--name=<name>'.", serviceName)
		log.Fatalf("- Was the service recently installed? It may still be initializing, Wait a bit and try again.")
	case response.StatusCode < 200 || response.StatusCode >= 300:
		log.Fatalf("HTTP %s Query for %s failed: %s",
			response.Request.Method, response.Request.URL, response.Status)
	}
	return response
}

func CreateHTTPJSONRequest(method, urlPath, jsonPayload string) *http.Request {
	return CreateHTTPDataRequest(method, urlPath, jsonPayload, "application/json")
}

func CreateHTTPDataRequest(method, urlPath, jsonPayload, contentType string) *http.Request {
	return CreateHTTPRawRequest(method, urlPath, "", jsonPayload, contentType)
}

func CreateHTTPQueryRequest(method, urlPath, urlQuery string) *http.Request {
	return CreateHTTPRawRequest(method, urlPath, urlQuery, "", "")
}

func CreateHTTPRequest(method, urlPath string) *http.Request {
	return CreateHTTPRawRequest(method, urlPath, "", "", "")
}

func CreateHTTPRawRequest(method, urlPath, urlQuery, payload, contentType string) *http.Request {
	// get data from CLI, if overrides were not provided by user:
	if len(dcosUrl) == 0 {
		dcosUrl = RequiredCLIConfigValue(
			"core.dcos_url",
			"DC/OS Cluster URL",
			"Run 'dcos config set core.dcos_url http://your-cluster.com' to configure.")
	}
	parsedUrl, err := url.Parse(dcosUrl)
	if err != nil {
		log.Fatalf("Unable to parse DC/OS Cluster URL '%s': %s", dcosUrl, err)
	}
	if len(dcosAuthToken) == 0 {
		dcosAuthToken = RequiredCLIConfigValue(
			"core.dcos_acs_token",
			"DC/OS Authentication Token",
			"Run 'dcos auth login' to log in to the cluster.")
	}
	parsedUrl.Path = path.Join("service", serviceName, urlPath)
	parsedUrl.RawQuery = urlQuery
	if Verbose {
		log.Printf("HTTP Query: %s %s", method, parsedUrl)
		if len(payload) != 0 {
			log.Printf("  Payload: %s", payload)
		}
	}
	request, err := http.NewRequest(method, parsedUrl.String(), bytes.NewReader([]byte(payload)))
	if err != nil {
		log.Fatalf("Failed to create HTTP %s request for %s: %s", method, parsedUrl, err)
	}
	request.Header.Set("Authorization", fmt.Sprintf("token=%s", dcosAuthToken))
	if len(contentType) != 0 {
		request.Header.Set("Content-Type", contentType)
	}
	return request
}
