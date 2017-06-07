package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"os"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// Fake functions that allow us to assert against output
var PrintMessage = printMessage
var PrintMessageAndExit = printMessageAndExit

func printMessage(format string, a ...interface{}) (int, error) {
	message := fmt.Sprintf(format, a...)
	return fmt.Println(message)
}

func printMessageAndExit(format string, a ...interface{}) (int, error) {
	PrintMessage(format, a...)
	os.Exit(1)
	return 0, nil
}

func printResponseError(response *http.Response) {
	PrintMessage("HTTP %s Query for %s failed: %s",
		response.Request.Method, response.Request.URL, response.Status)
}

func printResponseErrorAndExit(response *http.Response) {
	PrintMessageAndExit("HTTP %s Query for %s failed: %s",
		response.Request.Method, response.Request.URL, response.Status)
}

func printServiceNameErrorAndExit(response *http.Response) {
	if config.Verbose {
		printResponseError(response)
	}
	PrintMessage("Did you provide the correct service name? Currently using '%s', specify a different name with '--name=<name>'.", config.ServiceName)
	PrintMessageAndExit("Was the service recently installed or updated? It may still be initializing, wait a bit and try again.")
}

func PrintJSONBytes(responseBytes []byte, request *http.Request) {
	var outBuf bytes.Buffer
	err := json.Indent(&outBuf, responseBytes, "", "  ")
	if err != nil {
		// Be permissive of malformed json, such as character codes in strings that are unknown to
		// Go's json: Warn in stderr, then print original to stdout.
		if request != nil {
			PrintMessage("Failed to prettify JSON response data from %s %s query: %s",
				request.Method, request.URL, err)
		} else {
			PrintMessage("Failed to prettify JSON response data: %s", err)
		}

		PrintMessage("Original data follows:")
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
		PrintMessageAndExit("Failed to read response data from %s %s query: %s",
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

func GetValueFromJSONResponse(responseBytes []byte, field string) ([]byte, error) {
	responseJSONBytes, err := UnmarshalJSON(responseBytes)
	if err != nil {
		return nil, err
	}
	return GetValueFromJSON(responseJSONBytes, field)
}

func GetValueFromJSON(jsonBytes map[string]interface{}, field string) ([]byte, error) {
	if valueJSON, present := jsonBytes[field]; present {
		valueBytes, err := json.Marshal(valueJSON)
		if err != nil {
			return nil, err
		}
		return valueBytes, nil
	}
	return nil, nil
}
