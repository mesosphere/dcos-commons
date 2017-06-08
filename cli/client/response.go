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

// PrintMessage is a placeholder function that wraps a call to
// fmt.Println(fmt.Sprintf()) to allow assertions against captured output.
var PrintMessage = printMessage

// PrintMessageAndExit is a placeholder function that wraps a call to
// PrintMessage() before exiting to allow assertions against captured output.
var PrintMessageAndExit = printMessageAndExit

func printMessage(format string, a ...interface{}) (int, error) {
	return fmt.Println(fmt.Sprintf(format, a...))
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

// PrintJSONBytes pretty prints responseBytes assuming it is valid JSON.
// If not valid, an error message will be printed and the original data will
// be printed.
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

// PrintJSON is a wrapper to PrintJSONBytes that attempts to read
// the body of a response into a []byte.
func PrintJSON(response *http.Response) {
	PrintJSONBytes(GetResponseBytes(response), response.Request)
}

// PrintResponseText prints out the body of a response as text.
func PrintResponseText(response *http.Response) {
	PrintMessage("%s\n", GetResponseText(response))
}

// GetResponseText attempts to the read the body of a response
// and cast it to a string.
func GetResponseText(response *http.Response) string {
	return string(GetResponseBytes(response))
}

// GetResponseBytes reads the body of a response into a []byte.
func GetResponseBytes(response *http.Response) []byte {
	defer response.Body.Close()
	responseBytes, err := ioutil.ReadAll(response.Body)
	if err != nil {
		PrintMessageAndExit("Failed to read response data from %s %s query: %s",
			response.Request.Method, response.Request.URL, err)
	}
	return responseBytes
}

// UnmarshalJSON unmarshals a []byte of JSON into a map[string]interface{}
// for easy handling of JSON responses that have variable field.
func UnmarshalJSON(jsonBytes []byte) (map[string]interface{}, error) {
	var responseJSON map[string]interface{}
	err := json.Unmarshal(jsonBytes, &responseJSON)
	if err != nil {
		return nil, err
	}
	return responseJSON, nil
}

// GetValueFromJSONResponse is a utility function to unmarshal a []byte of JSON
// and fetch the value of a specific field from it.
func GetValueFromJSONResponse(responseBytes []byte, field string) ([]byte, error) {
	responseJSONBytes, err := UnmarshalJSON(responseBytes)
	if err != nil {
		return nil, err
	}
	return GetValueFromJSON(responseJSONBytes, field)
}

// GetValueFromJSON retrieves the value of a specific field from an umarshaled map[string]interface
// of JSON. If the field does not exist, this returns nil.
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
