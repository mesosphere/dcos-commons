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
	Found    interface{}
	Expected []interface{}
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
	writer := bufio.NewWriter(&buf)
	fmt.Fprintf(writer, "Unable to update %s to requested version: \"%s\"\n", config.ServiceName, data.UpdateVersion)
	if len(data.ValidVersions) > 0 {
		fmt.Fprintf(writer, "Valid package versions are: %s", PrettyPrintSlice(data.ValidVersions))
	} else {
		fmt.Fprint(writer, "No valid package versions to update to.")
	}
	writer.Flush()
	return fmt.Errorf(buf.String())
}

func createJSONMismatchError(data cosmosData) error {
	var buf bytes.Buffer
	writer := bufio.NewWriter(&buf)
	fmt.Fprintf(writer, "Unable to update %s to requested configuration: options JSON failed validation.\n\n", config.ServiceName)

	tWriter := tabwriter.NewWriter(writer, 0, 4, 1, ' ', 0)
	fmt.Fprintf(tWriter, "Field\tError\t\n")
	fmt.Fprintf(tWriter, "-----\t-----\t")
	for _, err := range data.Errors {
		fmt.Fprintf(tWriter, "\n%s\t%s\t", err.Instance.Pointer, err.Message)
	}
	tWriter.Flush()
	writer.Flush()
	return fmt.Errorf(buf.String())
}

func createAppIDChangedError(data cosmosData) error {
	errorString := `Could not update service name from "%s" to "%s".
The service name cannot be changed once installed. Ensure service.name is set to "%s" in options JSON.`
	return fmt.Errorf(errorString, data.OldAppID, data.NewAppID, data.OldAppID)
}

func createAppNotFoundError(data cosmosData) error {
	errorString := `Unable to find the service named '%s'.
Possible causes:
- Did you provide the correct service name? Specify a service name with '--name=<name>', or with 'dcos config set %s.service_name <name>'.
- Was the service recently installed or updated? It may still be initializing, wait a bit and try again.`
	return fmt.Errorf(errorString, config.ServiceName, config.ModuleName)
}

func parseCosmosHTTPErrorResponse(response *http.Response, body []byte) error {
	var errorResponse cosmosErrorResponse
	err := json.Unmarshal(body, &errorResponse)
	if err != nil {
		printMessage("Error unmarshalling Cosmos Error: %v", err.Error())
		return createResponseError(response, body)
	}
	if errorResponse.ErrorType == "" {
		return createResponseError(response, body)
	}
	switch errorResponse.ErrorType {
	case badVersionUpdate:
		return createBadVersionError(errorResponse.Data)
	case jsonSchemaMismatch:
		return createJSONMismatchError(errorResponse.Data)
	case appIDChanged:
		return createAppIDChangedError(errorResponse.Data)
	case marathonAppNotFound:
		return createAppNotFoundError(errorResponse.Data)
	default:
		if config.Verbose {
			PrintJSONBytes(body)
		}
		return fmt.Errorf("Could not execute command: %s (%s)", errorResponse.Message, errorResponse.ErrorType)
	}
}

func checkCosmosHTTPResponse(response *http.Response, body []byte) error {
	switch {
	case response.StatusCode == http.StatusNotFound:
		PrintVerbose(createResponseError(response, body).Error())
		return fmt.Errorf("This command requires Enterprise DC/OS 1.10 or newer.")
	case response.StatusCode == http.StatusBadRequest:
		return parseCosmosHTTPErrorResponse(response, body)
	case response.StatusCode == http.StatusInternalServerError:
		return createResponseError(response, body)
	}
	// Fall back to defaultResponseCheck()
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
	cosmosURL := OptionalCLIConfigValue("package.cosmos_url")
	if len(cosmosURL) > 0 {
		// Use specified Cosmos URL: https://<cosmos_url>/service/describe
		return CreateURL(cosmosURL, path.Join("service", urlPath), "")
	} else {
		// Use default Cosmos service path within DC/OS: https://<dcos_url>/cosmos/service/describe
		return CreateURL(GetDCOSURL(), path.Join("cosmos", "service", urlPath), "")
	}
}
