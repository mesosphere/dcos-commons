package client

import (
	"crypto/x509"
	"fmt"
	"io/ioutil"
	"net/http"
	"net/url"

	"github.com/mesosphere/dcos-commons/cli/config"
)

type responseCheck func(response *http.Response, body []byte) error

var customCheck responseCheck

// SetCustomResponseCheck sets a custom responseCheck that
// can be used for more advanced handling of HTTP responses.
func SetCustomResponseCheck(check responseCheck) {
	customCheck = check
}

// CheckHTTPResponse checks the HTTP response and the returned error, then returns the response payload and/or a better user-facing error.
func CheckHTTPResponse(response *http.Response, err error) ([]byte, error) {
	// Check for anything to return from the query itself
	switch err.(type) {
	case *url.Error:
		// extract wrapped error
		err = err.(*url.Error).Err
	}
	if err != nil {
		switch err.(type) {
		case x509.UnknownAuthorityError:
			// custom suggestions for a certificate error:
			return nil, fmt.Errorf(`
HTTP %s Query for %s failed: %s
- Is the cluster CA certificate configured correctly? Check 'dcos config show core.ssl_verify'.
- To ignore the unvalidated certificate and force your command (INSECURE), use --force-insecure. For more syntax information`,
				response.Request.Method, response.Request.URL, err)
		default:
			return nil, fmt.Errorf(`
HTTP %s Query for %s failed: %s
- Is 'core.dcos_url' set correctly? Check 'dcos config show core.dcos_url'.
- Is 'core.dcos_acs_token' set correctly? Run 'dcos auth login' to log in.
- Are any needed proxy settings set correctly via HTTP_PROXY/HTTPS_PROXY/NO_PROXY? Check with your network administrator.
For more syntax information`,
				response.Request.Method, response.Request.URL, err)
		}
	}

	// Now look at the content of the response itself for any errors.
	body, err := getResponseBytes(response)
	if err != nil {
		return nil, fmt.Errorf("Failed to read response data from %s %s query: %s",
			response.Request.Method, response.Request.URL, err)
	}
	if response.ContentLength > 0 {
		PrintVerbose("Response (%d byte payload): %s\n%s", response.ContentLength, response.Status, body)
	} else {
		PrintVerbose("Response: %s", response.Status)
	}
	if customCheck != nil {
		err := customCheck(response, body)
		if err != nil {
			return body, err
		}
	}
	err = defaultResponseCheck(response, body)
	if err != nil && response.ContentLength > 0 {
		// Print response payload if there's an error, and add "query failed" so that added ", try --help" looks better
		err = fmt.Errorf(err.Error() + "\nResponse data (%d bytes): %s\nHTTP query failed", response.ContentLength, body)
	}
	return body, err
}

func defaultResponseCheck(response *http.Response, body []byte) error {
	switch {
	case response.StatusCode == http.StatusUnauthorized:
		return fmt.Errorf(`
Got 401 Unauthorized response from %s:
- Bad auth token? Run 'dcos auth login' to log in`, response.Request.URL)
	case response.StatusCode == http.StatusNotFound:
		return fmt.Errorf(`
Got 404 Not Found response from %s:
- The service scheduler may have been unable to find an item that was specified in your request.
- The DC/OS cluster may have been unable to find a service named "%s". Specify a service name with '--name=<name>', or with 'dcos config set %s.service_name <name>'. For more syntax information`,
			response.Request.URL, config.ServiceName, config.ModuleName)
	case response.StatusCode == http.StatusInternalServerError || response.StatusCode == http.StatusBadGateway:
		return fmt.Errorf(`
Could not reach the service scheduler with name '%s':
- Was the service recently installed or updated? It may still be initializing, wait a bit and try again.
- Did you provide the correct service name? Specify a service name with '--name=<name>', or with 'dcos config set %s.service_name <name>'. For more syntax information`,
			config.ServiceName, config.ModuleName)
	case response.StatusCode < 200 || response.StatusCode >= 300:
		return createResponseError(response, body)
	}
	return nil
}

func getResponseBytes(response *http.Response) ([]byte, error) {
	defer response.Body.Close()
	responseBytes, err := ioutil.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	return responseBytes, nil
}

func createResponseError(response *http.Response, body []byte) error {
	if len(body) > 0 {
		return fmt.Errorf("HTTP %s Query for %s failed: %s\nResponse: %s",
			response.Request.Method, response.Request.URL, response.Status, string(body))
	} else {
		return fmt.Errorf("HTTP %s Query for %s failed: %s",
			response.Request.Method, response.Request.URL, response.Status)
	}
}
