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
func HTTPCosmosPostJSON(urlPath, jsonPayload string) ([]byte, error) {
	SetCustomResponseCheck(checkCosmosHTTPResponse)
	return checkHTTPResponse(httpQuery(createCosmosHTTPJSONRequest("POST", urlPath, jsonPayload)))
}

func createBadVersionError(response *http.Response, data map[string]interface{}) error {
	requestedVersion, _ := GetValueFromJSON(data, "updateVersion")
	validVersions, _ := GetValueFromJSON(data, "validVersions")
	if config.Verbose {
		printResponseError(response)
	}

	errorString := `Unable to update %s to requested version: %s
Valid versions are: %s`

	return fmt.Errorf(errorString, config.ServiceName, requestedVersion, validVersions)
}

func parseCosmosHTTPErrorResponse(response *http.Response, body []byte) error {
	responseJSON, err := UnmarshalJSON(body)
	if err != nil {
		return createResponseError(response)
	}
	if errorType, present := responseJSON["type"]; present {
		message := responseJSON["message"]
		switch errorType {
		case "MarathonAppNotFound":
			return createServiceNameError(response)
		case "BadVersionUpdate":
			return createBadVersionError(response, responseJSON["data"].(map[string]interface{}))
		default:
			if config.Verbose {
				PrintMessage("Cosmos error: %s: %s", errorType, message)
			}
		}
	}
	return createResponseError(response)
}

func checkCosmosHTTPResponse(response *http.Response, body []byte) error {
	switch {
	case response.StatusCode == http.StatusNotFound:
		if config.Verbose {
			printResponseError(response)
		}
		return fmt.Errorf("dcos %s %s requires Enterprise DC/OS 1.10 or newer.", config.ModuleName, config.Command)
	case response.StatusCode == http.StatusBadRequest:
		return parseCosmosHTTPErrorResponse(response, body)
	}
	return nil
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
