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

const cosmosURLConfigKey = "package.cosmos_url"

// Cosmos error types
const (
	appIDChanged        = "AppIdChanged"
	badVersionUpdate    = "BadVersionUpdate"
	jsonSchemaMismatch  = "JsonSchemaMismatch"
	marathonAppNotFound = "MarathonAppNotFound"
)

// HTTPCosmosPostJSON triggers a HTTP POST request containing jsonPayload to
// https://dcos.cluster/cosmos/service/<urlPath>
func HTTPCosmosPostJSON(urlPath, jsonPayload string) ([]byte, error) {
	SetCustomResponseCheck(checkCosmosHTTPResponse)
	return CheckHTTPResponse(HTTPQuery(createCosmosHTTPJSONRequest("POST", urlPath, jsonPayload)))
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
	NewAppID      string
	OldAppID      string
	UpdateVersion string
	ValidVersions []string
}

type cosmosErrorResponse struct {
	ErrorType string `json:"type"`
	Message   string
	Data      cosmosData
}

func createBadVersionError(data cosmosData) error {
	var buf bytes.Buffer
	buf.WriteString(fmt.Sprintf("Unable to update %s to requested version: \"%s\"\n", config.ServiceName, data.UpdateVersion))
	if len(data.ValidVersions) > 0 {
		validVersions := PrettyPrintSlice(data.ValidVersions)
		buf.WriteString(fmt.Sprintf("Valid package versions are: %s", validVersions))
	} else {
		buf.WriteString("No valid package versions to update to.")
	}
	return fmt.Errorf(buf.String())
}
func createJSONMismatchError(data cosmosData) error {
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

func createAppIDChangedError(data cosmosData) error {
	errorString := `Could not update service name from "%s" to "%s".
The service name cannot be changed once installed. Ensure service.name is set to "%s" in options JSON.`
	return fmt.Errorf(errorString, data.OldAppID, data.NewAppID, data.OldAppID)
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
		case appIDChanged:
			return createAppIDChangedError(errorResponse.Data)
		case badVersionUpdate:
			return createBadVersionError(errorResponse.Data)
		case jsonSchemaMismatch:
			return createJSONMismatchError(errorResponse.Data)
		case marathonAppNotFound:
			return createServiceNameError()
		default:
			if config.Verbose {
				PrintJSONBytes(body)
			}
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
	return CreateHTTPRawRequest(method, createCosmosURL(urlPath), jsonPayload, acceptHeader, contentTypeHeader)
}

func createCosmosURL(urlPath string) *url.URL {
	// Try to fetch the Cosmos URL from the system configuration
	if len(config.CosmosURL) == 0 {
		config.CosmosURL = OptionalCLIConfigValue(cosmosURLConfigKey)
	}

	// Use Cosmos URL if we have it specified
	if len(config.CosmosURL) > 0 {
		joinedURLPath := path.Join("service", urlPath) // e.g. https://<cosmos_url>/service/describe
		return CreateURL(config.CosmosURL, joinedURLPath, "")
	}
	GetDCOSURL()
	joinedURLPath := path.Join("cosmos", "service", urlPath) // e.g. https://<dcos_url>/cosmos/service/describe
	return CreateURL(config.DcosURL, joinedURLPath, "")
}
