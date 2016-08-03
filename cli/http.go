package cli

import (
	"bytes"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"path"
)

var (
	dcosAuthToken string
	dcosUrl       string
	serviceName   string
)

func HTTPGet(urlPath string) *http.Response {
	return HTTPQuery(CreateHTTPRequest("GET", urlPath), true)
}
func HTTPGetData(urlPath, payload, contentType string) *http.Response {
	return HTTPQuery(CreateHTTPDataRequest("GET", urlPath, payload, contentType), true)
}
func HTTPGetJSON(urlPath, jsonPayload string) *http.Response {
	return HTTPQuery(CreateHTTPJSONRequest("GET", urlPath, jsonPayload), true)
}

func HTTPDelete(urlPath string) *http.Response {
	return HTTPQuery(CreateHTTPRequest("DELETE", urlPath), true)
}
func HTTPDeleteData(urlPath, payload, contentType string) *http.Response {
	return HTTPQuery(CreateHTTPDataRequest("DELETE", urlPath, payload, contentType), true)
}
func HTTPDeleteJSON(urlPath, jsonPayload string) *http.Response {
	return HTTPQuery(CreateHTTPJSONRequest("DELETE", urlPath, jsonPayload), true)
}

func HTTPPost(urlPath string) *http.Response {
	return HTTPQuery(CreateHTTPRequest("POST", urlPath), true)
}
func HTTPPostData(urlPath, payload, contentType string) *http.Response {
	return HTTPQuery(CreateHTTPDataRequest("POST", urlPath, payload, contentType), true)
}
func HTTPPostJSON(urlPath, jsonPayload string) *http.Response {
	return HTTPQuery(CreateHTTPJSONRequest("POST", urlPath, jsonPayload), true)
}

func HTTPPut(urlPath string) *http.Response {
	return HTTPQuery(CreateHTTPRequest("PUT", urlPath), true)
}
func HTTPPutData(urlPath, payload, contentType string) *http.Response {
	return HTTPQuery(CreateHTTPDataRequest("PUT", urlPath, payload, contentType), true)
}
func HTTPPutJSON(urlPath, jsonPayload string) *http.Response {
	return HTTPQuery(CreateHTTPJSONRequest("PUT", urlPath, jsonPayload), true)
}

func HTTPQuery(request *http.Request, checkResponseStatus bool) *http.Response {
	client := &http.Client{}
	response, err := client.Do(request)
	if err != nil {
		// err contains method/parsedUrl. avoid repeating ourselves:
		log.Printf("HTTP Query failed: %s", err)
		log.Printf("- Is 'core.dcos_url' set correctly? Check 'dcos config show core.dcos_url'.")
		log.Fatalf("- Is 'core.dcos_acs_token' set correctly? Call 'dcos auth login' to log in.")
	}
	if response.StatusCode == 401 {
		log.Fatalf("Got 401 Unauthorized response from %s. Bad auth token? Call 'dcos auth login' to log in.", request.URL)
	}
	if checkResponseStatus {
		switch {
		case response.StatusCode == 500:
			log.Printf("HTTP %s Query for %s failed: %s", request.Method, request.URL, response.Status)
			log.Printf("- Did you select the correct service name? Use '--name=<name>' to assign the name.")
			log.Fatalf("- Is the service recently installed and still initializing? Wait a bit and try again.")
		case response.StatusCode < 200 || response.StatusCode >= 300:
			log.Fatalf("HTTP %s Query for %s failed: %s", request.Method, request.URL, response.Status)
		}
	}
	if Verbose {
		log.Printf("Response: %s (%d bytes)", response.Status, response.ContentLength)
	}
	return response
}

func CreateHTTPJSONRequest(method, urlPath, jsonPayload string) *http.Request {
	return CreateHTTPDataRequest(method, urlPath, jsonPayload, "application/json")
}

func CreateHTTPRequest(method, urlPath string) *http.Request {
	return CreateHTTPDataRequest(method, urlPath, "", "")
}

func CreateHTTPDataRequest(method, urlPath, payload, contentType string) *http.Request {
	// get data from CLI, if overrides were not provided by user:
	if len(dcosUrl) == 0 {
		dcosUrl = GetCheckedDCOSCLIConfigValue(
			"core.dcos_url",
			"DC/OS Cluster URL",
			"Run 'dcos config set core.dcos_url http://your-cluster.com' to configure.")
	}
	parsedUrl, err := url.Parse(dcosUrl)
	if err != nil {
		log.Fatalf("Unable to parse DC/OS Cluster URL '%s': %s", dcosUrl, err)
	}
	if len(dcosAuthToken) == 0 {
		dcosAuthToken = GetCheckedDCOSCLIConfigValue(
			"core.dcos_acs_token",
			"DC/OS Authentication Token",
			"Run 'dcos auth login' to log in to the cluster.")
	}
	parsedUrl.Path = path.Join("service", serviceName, urlPath)
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
