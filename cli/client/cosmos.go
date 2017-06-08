package client

import (
	"fmt"
	"net/http"
	"net/url"
	"path"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// HTTPCosmosPostJSON triggers a HTTP POST request containing jsonPayload to
// https://dcos.cluster/cosmos/service/<urlPath>
func HTTPCosmosPostJSON(urlPath, jsonPayload string) *http.Response {
	return checkCosmosHTTPResponse(httpQuery(createCosmosHTTPJSONRequest("POST", urlPath, jsonPayload)))
}

func printBadVersionErrorAndExit(response *http.Response, data map[string]interface{}) {
	requestedVersion, _ := GetValueFromJSON(data, "updateVersion")
	validVersions, _ := GetValueFromJSON(data, "validVersions")
	if config.Verbose {
		printResponseError(response)
	}
	PrintMessage("Unable to update %s to requested version: %s", config.ServiceName, requestedVersion)
	PrintMessageAndExit("Valid versions are: %s", validVersions)
}

func parseCosmosHTTPErrorResponse(response *http.Response) {
	responseJSON, err := UnmarshalJSON(GetResponseBytes(response))
	if err != nil {
		printResponseErrorAndExit(response)
	}
	if errorType, present := responseJSON["type"]; present {
		message := responseJSON["message"]
		switch errorType {
		case "MarathonAppNotFound":
			printServiceNameErrorAndExit(response)
		case "BadVersionUpdate":
			printBadVersionErrorAndExit(response, responseJSON["data"].(map[string]interface{}))
		default:
			if config.Verbose {
				PrintMessage("Cosmos error: %s: %s", errorType, message)
			}
			printResponseErrorAndExit(response)
		}
	}
}

func checkCosmosHTTPResponse(response *http.Response) *http.Response {
	switch {
	case response.StatusCode == http.StatusNotFound:
		if config.Verbose {
			printResponseError(response)
		}
		PrintMessageAndExit("dcos %s %s requires Enterprise DC/OS 1.10 or newer.", config.ModuleName, config.Command)
	case response.StatusCode == http.StatusBadRequest:
		parseCosmosHTTPErrorResponse(response)
	default:
		return checkHTTPResponse(response)
	}
	return response
}

func createCosmosHTTPJSONRequest(method, urlPath, jsonPayload string) *http.Request {
	// NOTE: this explicitly only allows use of /service/ endpoints within Cosmos. See DCOS-15772
	// for the "correct" solution to allow cleaner use of /package/ endpoints.
	endpoint := strings.Replace(urlPath, "/", ".", -1)
	acceptHeader := fmt.Sprintf("application/vnd.dcos.service.%s-response+json;charset=utf-8;version=v1", endpoint)
	contentTypeHeader := fmt.Sprintf("application/vnd.dcos.service.%s-request+json;charset=utf-8;version=v1", endpoint)
	return createHTTPRawRequest(method, createCosmosURL(urlPath), jsonPayload, acceptHeader, contentTypeHeader)
}

func createCosmosURL(urlPath string) *url.URL {
	// Try to fetch the Cosmos URL from the system configuration
	if len(config.CosmosURL) == 0 {
		config.CosmosURL = OptionalCLIConfigValue("package.cosmos_url")
	}

	// Use Cosmos URL if we have it specified
	if len(config.CosmosURL) > 0 {
		joinedURLPath := path.Join("service", urlPath) // e.g. https://<cosmos_url>/service/describe
		return createURL(config.CosmosURL, joinedURLPath, "")
	}
	getDCOSURL()
	joinedURLPath := path.Join("cosmos", "service", urlPath) // e.g. https://<dcos_url>/cosmos/service/describe
	return createURL(config.DcosURL, joinedURLPath, "")
}
