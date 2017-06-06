package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// Fake functions that allow us to assert against output
var LogMessage = log.Printf
var LogMessageAndExit = log.Fatalf
var PrintMessage = fmt.Printf

func printResponseError(response *http.Response) {
	LogMessage("HTTP %s Query for %s failed: %s",
		response.Request.Method, response.Request.URL, response.Status)
}

func printResponseErrorAndExit(response *http.Response) {
	LogMessageAndExit("HTTP %s Query for %s failed: %s",
		response.Request.Method, response.Request.URL, response.Status)
}

func printServiceNameErrorAndExit(response *http.Response) {
	printResponseError(response)
	LogMessage("- Did you provide the correct service name? Currently using '%s', specify a different name with '--name=<name>'.", config.ServiceName)
	LogMessageAndExit("- Was the service recently installed or updated? It may still be initializing, wait a bit and try again.")
}

func PrintJSONBytes(responseBytes []byte, request *http.Request) {
	var outBuf bytes.Buffer
	err := json.Indent(&outBuf, responseBytes, "", "  ")
	if err != nil {
		// Be permissive of malformed json, such as character codes in strings that are unknown to
		// Go's json: Warn in stderr, then print original to stdout.
		if request != nil {
			LogMessage("Failed to prettify JSON response data from %s %s query: %s",
				request.Method, request.URL, err)
		} else {
			LogMessage("Failed to prettify JSON response data: %s", err)
		}

		LogMessage("Original data follows:")
		outBuf = *bytes.NewBuffer(responseBytes)
	}
	PrintMessage("%s\n", outBuf.String())
}

func PrintJSON(response *http.Response) {
	PrintJSONBytes(GetResponseBytes(response), response.Request)
}

func PrintResponseText(response *http.Response) {
	PrintMessage("%s\n", GetResponseText(response))
}

func GetResponseText(response *http.Response) string {
	return string(GetResponseBytes(response))
}

func GetResponseBytes(response *http.Response) []byte {
	defer response.Body.Close()
	responseBytes, err := ioutil.ReadAll(response.Body)
	if err != nil {
		LogMessageAndExit("Failed to read response data from %s %s query: %s",
			response.Request.Method, response.Request.URL, err)
	}
	return responseBytes
}

func UnmarshalJSON(jsonBytes []byte) (map[string]interface{}, error) {
	var responseJSON map[string]interface{}
	err := json.Unmarshal(jsonBytes, &responseJSON)
	if err != nil {
		return nil, err
	}
	return responseJSON, nil
}
