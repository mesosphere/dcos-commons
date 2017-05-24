package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// Fake functions that allow us to assert against output
var logMessage = log.Printf
var logMessageAndExit = log.Fatalf

func printError(response *http.Response) {
	logMessage("HTTP %s Query for %s failed: %s",
		response.Request.Method, response.Request.URL, response.Status)
}

func printErrorAndExit(response *http.Response) {
	logMessageAndExit("HTTP %s Query for %s failed: %s",
		response.Request.Method, response.Request.URL, response.Status)
}

func printServiceNameErrorAndExit(response *http.Response) {
	printError(response)
	logMessage("- Did you provide the correct service name? Currently using '%s', specify a different name with '--name=<name>'.", config.ServiceName)
	logMessageAndExit("- Was the service recently installed? It may still be initializing, wait a bit and try again.")
}

func PrintText(text string) {
	fmt.Fprintf(os.Stdout, "%s\n", text)
}

func PrintJSONBytes(responseBytes []byte, request *http.Request) {
	var outBuf bytes.Buffer
	err := json.Indent(&outBuf, responseBytes, "", "  ")
	if err != nil {
		// Be permissive of malformed json, such as character codes in strings that are unknown to
		// Go's json: Warn in stderr, then print original to stdout.
		if request != nil {
			log.Printf("Failed to prettify JSON response data from %s %s query: %s",
				request.Method, request.URL, err)
		} else {
			log.Printf("Failed to prettify JSON response data: %s", err)
		}

		log.Printf("Original data follows:")
		outBuf = *bytes.NewBuffer(responseBytes)
	}
	outBuf.WriteTo(os.Stdout)
	fmt.Print("\n")
}

func PrintJSON(response *http.Response) {
	PrintJSONBytes(GetResponseBytes(response), response.Request)
}

func PrintResponseText(response *http.Response) {
	PrintText(GetResponseText(response))
}

func GetResponseText(response *http.Response) string {
	return string(GetResponseBytes(response))
}

func GetResponseBytes(response *http.Response) []byte {
	defer response.Body.Close()
	responseBytes, err := ioutil.ReadAll(response.Body)
	if err != nil {
		log.Fatalf("Failed to read response data from %s %s query: %s",
			response.Request.Method, response.Request.URL, err)
	}
	return responseBytes
}

func UnmarshalJSON(jsonBytes []byte) (map[string]interface{}, error) {
	var responseJSON map[string]interface{}
	err := json.Unmarshal([]byte(jsonBytes), &responseJSON)
	if err != nil {
		return nil, err
	}
	return responseJSON, nil
}
