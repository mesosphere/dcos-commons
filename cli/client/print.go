package client

import (
	"bytes"
	"encoding/json"
	"fmt"
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

func createResponseError(response *http.Response) error {
	return fmt.Errorf("HTTP %s Query for %s failed: %s",
		response.Request.Method, response.Request.URL, response.Status)
}

func printResponseErrorAndExit(response *http.Response) {
	PrintMessageAndExit(createResponseError(response).Error())
}

func createServiceNameError(response *http.Response) error {
	errorString := `Could not reach the service scheduler with name '%s'.
Did you provide the correct service name? Specify a different name with '--name=<name>'.
Was the service recently installed or updated? It may still be initializing, wait a bit and try again.`
	return fmt.Errorf(errorString, config.ServiceName)
}

func printServiceNameErrorAndExit(response *http.Response) {
	// TODO: check to see what the actual service state is
	if config.Verbose {
		printResponseError(response)
	}
	PrintMessageAndExit(createServiceNameError(response).Error())
}

// PrintJSONBytes pretty prints responseBytes assuming it is valid JSON.
// If not valid, an error message will be printed and the original data will
// be printed.
func PrintJSONBytes(responseBytes []byte) {
	var outBuf bytes.Buffer
	err := json.Indent(&outBuf, responseBytes, "", "  ")
	if err != nil {
		// Be permissive of malformed json, such as character codes in strings that are unknown to
		// Go's json: Warn in stderr, then print original to stdout.
		PrintMessage("Failed to prettify JSON response data: %s", err)
		PrintMessage("Original data follows:")
		outBuf = *bytes.NewBuffer(responseBytes)
	}
	PrintMessage("%s\n", outBuf.String())
}

// PrintResponseText prints out a byte array as text.
func PrintResponseText(body []byte) {
	PrintMessage("%s\n", string(body))
}
