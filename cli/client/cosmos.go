package client

import (
	"bytes"
	"fmt"
	"net/http"
	"net/url"
	"path"
	"strings"
	"text/tabwriter"

	"encoding/json"

	"bufio"

	"github.com/mesosphere/dcos-commons/cli/config"
)

const (
	cosmosURLConfigKey  = "package.cosmos_url"
	marathonAppNotFound = "MarathonAppNotFound"
	badVersionUpdate    = "BadVersionUpdate"
	jsonSchemaMismatch  = "JsonSchemaMismatch"
)

// HTTPCosmosPostJSON triggers a HTTP POST request containing jsonPayload to
// https://dcos.cluster/cosmos/service/<urlPath>
func HTTPCosmosPostJSON(urlPath, jsonPayload string) ([]byte, error) {
	SetCustomResponseCheck(checkCosmosHTTPResponse)
	return checkHTTPResponse(httpQuery(createCosmosHTTPJSONRequest("POST", urlPath, jsonPayload)))
}

type cosmosErrorInstance struct {
	Pointer string
}

type cosmosError struct {
	Keyword  string
	Message  string
	Found    string
	Expected []string
	Instance cosmosErrorInstance
	// deliberately omitting:
	// level
	// schema
	// domain
}

type cosmosData struct {
	Errors        []cosmosError
	UpdateVersion string
	ValidVersions []string
}

type cosmosErrorResponse struct {
	ErrorType string `json:"type"`
	Message   string
	Data      cosmosData
}

func createBadVersionError(data cosmosData) error {
	updateVersion := fmt.Sprintf("\"%s\"", data.UpdateVersion)
	validVersions := PrettyPrintSlice(data.ValidVersions)

	errorString := `Unable to update %s to requested version: %s
Valid versions are: %s`

	return fmt.Errorf(errorString, config.ServiceName, updateVersion, validVersions)
}
func createCosmosJSONMismatchError(data cosmosData) error {
	var buf bytes.Buffer
	writer := bufio.NewWriter(&buf)
	writer.WriteString("Unable to update %s to requested configuration: options JSON failed validation.")
	writer.WriteString("\n\n")
	tWriter := tabwriter.NewWriter(writer, 0, 4, 1, ' ', 0)
	fmt.Fprintf(tWriter, "Field\tError\t\n")
	fmt.Fprintf(tWriter, "-----\t-----\t")
	for _, err := range data.Errors {
		fmt.Fprintf(tWriter, "\n%s\t%s\t", err.Instance.Pointer, err.Message)
	}
	tWriter.Flush()
	writer.Flush()
	return fmt.Errorf(buf.String(), config.ServiceName)
}

func parseCosmosHTTPErrorResponse(response *http.Response, body []byte) error {
	var errorResponse cosmosErrorResponse
	err := json.Unmarshal(body, &errorResponse)
	if err != nil {
		printMessage(err.Error())
		return createResponseError(response)
	}
	if errorResponse.ErrorType != "" {
		switch errorResponse.ErrorType {
		case marathonAppNotFound:
			return createServiceNameError()
		case badVersionUpdate:
			return createBadVersionError(errorResponse.Data)
		case jsonSchemaMismatch:
			return createCosmosJSONMismatchError(errorResponse.Data)
		default:
			return fmt.Errorf("Could not execute command: %s", errorResponse.Message)
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
		config.CosmosURL = OptionalCLIConfigValue(cosmosURLConfigKey)
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
