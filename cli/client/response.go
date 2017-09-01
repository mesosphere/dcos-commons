package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
	"sort"
	"strings"

	"github.com/mesosphere/dcos-commons/cli/config"
)

type responseCheck func(response *http.Response, body []byte) error

var customCheck responseCheck

// SetCustomResponseCheck sets a custom responseCheck that
// can be used for more advanced handling of HTTP responses.
func SetCustomResponseCheck(check responseCheck) {
	customCheck = check
}

// CheckHTTPResponse checks the HTTP response
func CheckHTTPResponse(response *http.Response) ([]byte, error) {
	body, err := getResponseBytes(response)
	if err != nil {
		return nil, fmt.Errorf("Failed to read response data from %s %s query: %s",
			response.Request.Method, response.Request.URL, err)
	}
	if config.Verbose {
		if response.ContentLength > 0 {
			PrintMessage("Response (%d byte payload): %s\n%s", response.ContentLength, response.Status, body)
		} else {
			PrintMessage("Response: %s", response.Status)
		}
	}
	if customCheck != nil {
		err := customCheck(response, body)
		if err != nil {
			return body, err
		}
	}
	err = defaultResponseCheck(response)
	return body, err
}

func defaultResponseCheck(response *http.Response) error {
	switch {
	case response.StatusCode == http.StatusUnauthorized:
		errorString := `Got 401 Unauthorized response from %s
"- Bad auth token? Run 'dcos auth login' to log in.`
		return fmt.Errorf(errorString, response.Request.URL)
	case response.StatusCode == http.StatusInternalServerError || response.StatusCode == http.StatusBadGateway || response.StatusCode == http.StatusNotFound:
		return createServiceNameError()
	case response.StatusCode < 200 || response.StatusCode >= 300:
		return createResponseError(response)
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

// UnmarshalJSON unmarshals a []byte of JSON into a map[string]interface{}
// for easy handling of JSON responses that have variable fields.
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

// GetValueFromJSON retrieves the value of a specific field from an unmarshaled map[string]interface
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

// JSONBytesToArray is a utility method for unmarshaling a JSON string array
func JSONBytesToArray(bytes []byte) ([]string, error) {
	var array []string
	err := json.Unmarshal(bytes, &array)
	return array, err
}

// PrettyPrintSlice takes a slice (e.g. [0 1 2]), sorts it and returns a pretty printed string
// in the same style as a JSON string array (e.g. ["0", "1", "2"]).
func PrettyPrintSlice(slice []string) string {
	sort.Strings(slice)
	var buf bytes.Buffer
	buf.WriteString("[")
	var bits []string
	for _, element := range slice {
		bits = append(bits, fmt.Sprintf("\"%s\"", element))
	}
	buf.WriteString(strings.Join(bits, ", "))
	buf.WriteString("]")
	return buf.String()
}
