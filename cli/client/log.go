package client

import (
	"log"
	"net/http"

	"github.com/mesosphere/dcos-commons/cli/config"
)

// Fake functions that allow us to assert against output
var LogMessage = log.Printf
var LogMessageAndExit = log.Fatalf

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
	LogMessageAndExit("- Was the service recently installed? It may still be initializing, wait a bit and try again.")
}
